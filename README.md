# Pruebas ARSW — Estrategia Integral de Pruebas

Laboratorio de la asignatura *Arquitecturas de Software (ARSW)*. Construye una API de
pedidos (`Order API`) en Spring Boot y le aplica, capa por capa, distintos tipos de
pruebas: unitarias, de API, de integración, end-to-end de frontend y de carga,
cerrando con una estrategia de ejecución en CI/CD.

Este README documenta la teoría general del laboratorio y se irá completando a medida
que se avanza en cada sección.

## Idea central

Probar no es solo verificar que una funcionalidad responde. Probar es construir
**evidencia de calidad** sobre comportamiento, integración, rendimiento, experiencia
de usuario y confiabilidad del sistema. Una buena estrategia de pruebas protege
atributos de calidad como confiabilidad, mantenibilidad, rendimiento, seguridad,
disponibilidad y capacidad de evolución.

## Estrategia de pruebas por capas

```
Pruebas unitarias
      ↓
Pruebas de API
      ↓
Pruebas de integración
      ↓
Pruebas end-to-end de frontend
      ↓
Pruebas de carga
      ↓
Pipeline de validación (CI/CD)
```

Cada capa tiene un propósito, un costo y un nivel de confianza distintos: entre más
abajo en la pirámide, más rápida y barata es la prueba, pero menos "realista" es el
escenario que valida; entre más arriba, más cara y lenta, pero más cerca del
comportamiento real del sistema.

| Tipo de prueba | Qué valida | Herramientas |
|---|---|---|
| Unitaria | Lógica de una clase o función aislada | JUnit, Mockito |
| API | Códigos HTTP, JSON, validaciones, contrato de endpoint | MockMvc, REST Assured |
| Integración | Interacción entre servicio, repositorio y base de datos | `@SpringBootTest`, Testcontainers |
| Frontend automática | Flujos críticos desde la perspectiva del usuario | Playwright, Cypress, Testing Library |
| Carga | Comportamiento bajo múltiples usuarios/solicitudes concurrentes | k6, JMeter, Gatling |
| Pipeline | Ejecución repetible para evitar regresiones | GitHub Actions, GitLab CI, Jenkins |

**Recomendación de la guía:** no todas las pruebas deben ejecutarse en cada commit.
Las rápidas (unitarias, API) se ejecutan con frecuencia; las costosas (integración
completa, E2E, carga) se reservan para pull requests, releases o ambientes
controlados.

## Estructura del repositorio

```
Pruebas-ARSW/
├── README.md                          # este archivo: teoría general + guía de ejecución
├── .github/workflows/                 # pipeline de CI (sección 10)
│   └── arsw-testing-pipeline.yml
├── order-api/                         # backend Spring Boot (Order API)
│   ├── pom.xml
│   ├── src/main/java/edu/eci/arsw/testing/...
│   └── src/test/java/edu/eci/arsw/testing/...
├── frontend-tests/                    # pruebas E2E con Playwright (sección 8)
│   └── tests/orders.spec.js
└── load-tests/                        # scripts de carga con k6 (sección 9)
    ├── load-test.js
    └── load-test-stages.js
```

## Order API — proyecto base (sección 4)

API simple de pedidos, suficiente para aplicar los distintos tipos de pruebas sin
construir una aplicación de comercio electrónico completa.

```
Cliente / Frontend
      ↓
Order API (Spring Boot)
      ↓
Order Service
      ↓
Order Repository
      ↓
Base de datos (H2 en memoria)
```

**Stack:** Java 17+ (se usó Java 23 disponible localmente), Maven, Spring Boot 4.1.0,
Spring Web (starter `webmvc` en Boot 4), Spring Data JPA, Validation, H2 Database,
Spring Boot Test.

> **Nota sobre Spring Boot 4:** a partir de Boot 4 los starters se modularizaron más
> (p. ej. `spring-boot-starter-webmvc` en vez de `spring-boot-starter-web`, y starters
> de test específicos por módulo como `spring-boot-starter-data-jpa-test`,
> `spring-boot-starter-webmvc-test`). JUnit 5 y Mockito siguen llegando de forma
> transitiva a través de esos starters de test, igual que antes con
> `spring-boot-starter-test`.

### Paquetes

- `model` — entidad JPA `Order`.
- `dto` — `CreateOrderRequest` (entrada, con validaciones `@NotBlank`/`@Min`) y
  `OrderResponse` (salida).
- `repository` — `OrderRepository`, extiende `JpaRepository<Order, String>`.
- `service` — `OrderService`, regla de negocio: rechaza pedidos con `total > 5.000.000`.
- `controller` — `OrderController`, expone `POST /orders` y `GET /orders/{id}`.

### Cómo ejecutar

Desde `order-api/`:

```bash
# compilar
mvn compile

# ejecutar la aplicación (por defecto en el puerto 8080)
mvn spring-boot:run

# ejecutar las pruebas
mvn test
```

### Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/orders` | Crea un pedido. Body: `{ "customerId": "...", "total": 120000 }` |
| `GET` | `/orders/{id}` | Consulta un pedido por id |

## Sección 5 — Pruebas unitarias con JUnit y Mockito

`OrderServiceTest` prueba `OrderService` **aislado**, simulando `OrderRepository` con
Mockito (`mock(OrderRepository.class)`). No se levanta contexto de Spring: es la
prueba más rápida y barata de la pirámide.

- `shouldCreateOrderWhenRequestIsValid`: configura `repository.save(...)` con
  `when(...).thenReturn(...)` y valida que la respuesta tenga los datos esperados, y
  que `save` se haya invocado exactamente una vez (`verify(repository, times(1))`).
- `shouldRejectOrderWhenTotalExceedsLimit`: valida con `assertThrows` que un total
  mayor a 5.000.000 lanza `IllegalArgumentException`, y que `save` **nunca** se llama
  (`verify(repository, never())`) — confirma que la regla de negocio corta el flujo
  antes de tocar el repositorio.

Ejecutar solo esta clase: `mvn -Dtest=OrderServiceTest test`

> **Pendiente (Actividad 1 de la guía):** agregar pruebas para `findById` — una que
> retorne un pedido existente y otra que lance excepción cuando el pedido no existe.

## Sección 6 — Pruebas de API con MockMvc

`OrderControllerTest` prueba la capa web de forma aislada con `@WebMvcTest(OrderController.class)`:
Spring solo levanta el `OrderController` y el `MockMvc` asociado, **sin** base de
datos ni el resto del contexto. La dependencia `OrderService` se reemplaza con
`@MockitoBean`.

- `shouldCreateOrder`: hace `POST /orders` con un JSON válido y valida `201 Created`
  más el cuerpo de la respuesta con `jsonPath`.
- `shouldRejectInvalidRequest`: envía `customerId` vacío y `total` negativo; como el
  DTO usa `@NotBlank`/`@Min(1)` y el controlador tiene `@Valid`, Spring responde
  `400 Bad Request` automáticamente, sin que el código del controlador tenga que
  validarlo a mano.

> **Nota Spring Boot 4:** `@WebMvcTest` se movió de paquete respecto a Boot 3 —
> ahora es `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` en vez de
> `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`. El resto de
> clases de MockMvc (`MockMvc`, `MockMvcRequestBuilders`, `MockMvcResultMatchers`)
> siguen en Spring Framework (`org.springframework.test.web.servlet.*`) y no
> cambiaron.

Ejecutar solo esta clase: `mvn -Dtest=OrderControllerTest test`

> **Pendiente (Actividad 2 de la guía):** agregar una prueba para `GET /orders/{id}`
> que valide `200 OK`, el `id`, `customerId` y `status` del pedido.

## Sección 7 — Pruebas de integración

`OrderIntegrationTest` usa `@SpringBootTest`, que levanta el contexto **completo** de
Spring (servicio real + repositorio real + base de datos H2 en memoria real, sin
mocks). `shouldCreateAndFindOrder` crea un pedido a través del `OrderService` real y
luego lo busca por id, verificando que ambos objetos coincidan. Es la prueba más
lenta y costosa de las tres, pero la que da mayor confianza porque ejercita la
interacción real entre las capas.

Ejecutar solo esta clase: `mvn -Dtest=OrderIntegrationTest test`

### 7.1 Testcontainers (dependencias)

Se agregaron al `pom.xml` las dependencias `org.testcontainers:junit-jupiter` y
`org.testcontainers:postgresql` (scope `test`), más el `testcontainers-bom` en
`dependencyManagement` para fijar la versión (`1.21.3`) — el snippet de la guía no
incluye el BOM, así que sin él Maven no puede resolver la versión de esas
dependencias. Por ahora `OrderIntegrationTest` sigue usando el H2 en memoria por
defecto; usar un contenedor real de PostgreSQL en el test quedaría como una extensión
futura (reemplazar el `DataSource` por uno respaldado por un contenedor de
Testcontainers).

> **Pendiente (Actividad 3 de la guía):** explicar la diferencia entre la prueba
> unitaria del servicio, la prueba del controlador con MockMvc y la prueba de
> integración con `@SpringBootTest`, analizando rapidez, confianza y costo de
> mantenimiento.

## Sección 8 — Pruebas automáticas de frontend con Playwright

Playwright automatiza un navegador real para validar flujos completos **desde la
perspectiva del usuario** (end-to-end): abre la página, llena formularios, hace clic
y verifica lo que queda en pantalla. Es la capa más lenta y costosa antes de las
pruebas de carga, pero la que más se parece a lo que realmente hace un usuario.

`frontend-tests/tests/orders.spec.js` contiene dos pruebas de ejemplo de la guía:

- Verifica que la página principal cargue con un título esperado.
- Simula crear un pedido llenando `customer-id` y `order-total`, haciendo clic en
  `create-order`, y comprobando que `order-status` muestre `CREATED`.

**Importante — esto todavía no es ejecutable tal cual:** el test apunta a
`http://localhost:5173` (puerto típico de Vite) y a selectores `data-testid`, pero
**este repositorio no tiene un frontend implementado** — la guía no pide construir
uno, solo asume que existe. Por eso el reto final (sección 11) dice *"proponer o
implementar"* una prueba E2E: proponerla ya cumple el objetivo de aprendizaje.

Para que estos selectores funcionen alguna vez, los componentes del frontend
deberían incluir atributos `data-testid` (evita depender de textos o estilos que
cambian):

```html
<input data-testid="customer-id" />
<input data-testid="order-total" />
<button data-testid="create-order">Crear pedido</button>
<div data-testid="order-status"></div>
```

No se corrió `npm init playwright@latest` (sección 8.1): es un scaffolding
interactivo que descarga binarios de navegador (Chromium/Firefox/WebKit, varios
cientos de MB). Cuando tengas un frontend real contra el cual probar:

```bash
cd frontend-tests
npm init playwright@latest
npx playwright test
npx playwright show-report
```

> **Pendiente (Actividad 4 de la guía):** diseñar tres pruebas E2E — crear pedido
> exitosamente, mostrar error si el total es inválido, y consultar un pedido por
> id — indicando para cada una el flujo, los datos de entrada y el resultado
> esperado.

## Sección 9 — Pruebas de carga con k6

Las pruebas de carga validan cómo se comporta el sistema bajo múltiples usuarios o
solicitudes concurrentes: latencia, throughput, tasa de error y degradación. A
diferencia de las capas anteriores, aquí sí importa **cuántas** peticiones se
disparan y en cuánto tiempo, no solo si la respuesta es correcta.

- `load-tests/load-test.js`: carga simple y constante — 10 *usuarios virtuales*
  (`vus`) durante 30s, haciendo `GET /orders/ORD-1` y verificando `status 200` y
  `< 500ms` de respuesta.
- `load-tests/load-test-stages.js`: carga variable por *stages* (rampa de 0→10 en
  20s, 10→30 en 30s, 30→0 en 20s) contra `POST /orders`, con **thresholds**: la
  prueba falla si la tasa de error supera 5% o si el p95 de latencia supera 800ms.
  `__VU` es el id del usuario virtual actual, usado para generar un `customerId`
  distinto por usuario.

Para ejecutar, con la `order-api` corriendo en `localhost:8080`
(`mvn spring-boot:run` desde `order-api/`):

```bash
# instalar (elegir según el SO)
winget install k6            # Windows
brew install k6               # macOS
docker run --rm -i grafana/k6 run - < load-test.js   # Docker, sin instalar nada

# ejecutar
cd load-tests
k6 run load-test.js
k6 run load-test-stages.js
```

> **Pendiente (Actividad 5 de la guía):** ejecutar una prueba de carga con k6 y
> documentar usuarios virtuales, duración, total de solicitudes, porcentaje de
> fallos, p95 de latencia, resultado de los thresholds y una conclusión técnica.

## Sección 10 — Estrategia de pruebas en CI/CD

No todas las pruebas deben correr en cada commit: las rápidas (unitarias, API) sí;
las costosas (integración completa, E2E, carga) se reservan para pull requests,
releases o ambientes controlados.

`.github/workflows/arsw-testing-pipeline.yml` implementa el job `backend-tests` de
la guía: en cada `push` a `main` y en cada `pull_request`, hace checkout, instala
Java 17 (Temurin) y corre `mvn test`.

> **Ajuste sobre el snippet de la guía:** el `run: mvn test` original asume que el
> `pom.xml` está en la raíz del repo. Aquí vive en `order-api/`, así que agregué
> `working-directory: order-api` al step para que el pipeline realmente encuentre el
> proyecto.

Este pipeline es infraestructura compartida del laboratorio (no pertenece a la
sección de trabajo de una sola persona), por eso vive directamente en `develop`.

## Progreso del laboratorio

- [x] Sección 4 — Proyecto base Spring Boot
- [x] Sección 5 — Pruebas unitarias con JUnit y Mockito (código base; falta Actividad 1)
- [x] Sección 6 — Pruebas de API con MockMvc (código base; falta Actividad 2)
- [x] Sección 7 — Pruebas de integración + dependencias Testcontainers (falta Actividad 3)
- [x] Sección 8 — Prueba E2E de ejemplo con Playwright (código base; falta Actividad 4; frontend real no implementado)
- [x] Sección 9 — Scripts de carga con k6 (código base; falta Actividad 5: ejecutar y analizar)
- [x] Sección 10 — Pipeline de GitHub Actions para pruebas de backend
- [ ] Sección 11 — Actividad integradora y reto final
