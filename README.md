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
└── order-api/                         # backend Spring Boot (Order API)
    ├── pom.xml
    ├── src/main/java/edu/eci/arsw/testing/...
    └── src/test/java/edu/eci/arsw/testing/...
```

> Las carpetas `frontend-tests/` (Playwright, sección 8) y `load-tests/` (k6,
> sección 9) se agregan en la rama de trabajo de esa parte del laboratorio.

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

- `shouldReturnOrderWhenIdExists` (Actividad 1): simula `repository.findById("ORD-1")`
  devolviendo `Optional.of(order)` y valida que `OrderResponse` tenga los mismos
  datos que la entidad simulada.
- `shouldThrowExceptionWhenOrderNotFound` (Actividad 1): simula
  `repository.findById(...)` devolviendo `Optional.empty()` y valida con
  `assertThrows` que `findById` lanza `IllegalArgumentException` — cubre la rama del
  `orElseThrow` que sí se ejecuta.

Ejecutar solo esta clase: `mvn -Dtest=OrderServiceTest test`

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

- `shouldFindOrderById` (Actividad 2): mockea `service.findById("ORD-1")` y hace
  `GET /orders/ORD-1`, validando `200 OK` y el `id`, `customerId` y `status` en el
  cuerpo de la respuesta.

Ejecutar solo esta clase: `mvn -Dtest=OrderControllerTest test`

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

### Actividad 3 — Unitaria vs. MockMvc vs. Integración

| | Unitaria (`OrderServiceTest`) | API/MockMvc (`OrderControllerTest`) | Integración (`OrderIntegrationTest`) |
|---|---|---|---|
| **Qué levanta** | Nada de Spring; solo el objeto `OrderService` con un mock de `OrderRepository` | Solo la capa web (`@WebMvcTest`): el `DispatcherServlet`, el `OrderController` y la serialización JSON, con `OrderService` mockeado | El contexto completo de Spring: controlador, servicio, repositorio y base de datos H2 real |
| **Rapidez** | La más rápida (ms) — no hay arranque de contexto ni I/O | Intermedia — arranca un contexto parcial de Spring, más lenta que la unitaria pero mucho más liviana que una completa | La más lenta — arranca todo el contexto, configura JPA/Hibernate y una conexión real a base de datos |
| **Confianza** | Baja/media — prueba la lógica de negocio en aislamiento, pero no garantiza que el controlador, la serialización o el repositorio realmente funcionen juntos | Media — garantiza que las rutas, códigos HTTP, validaciones (`@Valid`) y el JSON de entrada/salida son correctos, pero el `OrderService` sigue siendo falso | Alta — es la única que demuestra que las piezas reales (servicio + repositorio + JPA + base de datos) efectivamente se integran y producen el resultado esperado |
| **Costo de mantenimiento** | Bajo — al mockear la única dependencia, cambios en la base de datos o en Spring no rompen este test; solo se rompe si cambia el contrato de `OrderService`/`OrderRepository` | Medio — puede romperse por cambios en rutas, DTOs o reglas de validación, aunque no depende de la base de datos | Alto — más frágil ante cambios de configuración (propiedades de datasource, mapeo JPA, versión de Spring Boot), y más costosa de depurar cuando falla porque hay más piezas en juego |

**Conclusión:** ninguna reemplaza a la otra — cada una detecta un tipo de error distinto (lógica de negocio vs. contrato HTTP vs. integración real), por eso la guía las organiza como una pirámide: muchas unitarias baratas en la base, menos pruebas de integración caras en la cima. Un cambio ideal se valida primero con la unitaria (feedback inmediato) y se confirma con la de integración antes de hacer merge.

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
- [x] Sección 5 — Pruebas unitarias con JUnit y Mockito (incluye Actividad 1)
- [x] Sección 6 — Pruebas de API con MockMvc (incluye Actividad 2)
- [x] Sección 7 — Pruebas de integración + dependencias Testcontainers (incluye Actividad 3)
- [x] Sección 10 — Pipeline de GitHub Actions para pruebas de backend
- [ ] Sección 8 — Pruebas E2E de frontend con Playwright (rama de esa parte del laboratorio)
- [ ] Sección 9 — Pruebas de carga con k6 (rama de esa parte del laboratorio)
- [ ] Sección 11 — Actividad integradora y reto final
