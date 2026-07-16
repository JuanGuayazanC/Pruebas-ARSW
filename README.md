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
├── README.md              # este archivo: teoría general + guía de ejecución
├── order-api/              # backend Spring Boot (Order API)
│   ├── pom.xml
│   ├── src/main/java/edu/eci/arsw/testing/...
│   └── src/test/java/edu/eci/arsw/testing/...
├── frontend-tests/         # pruebas E2E con Playwright (sección 8, pendiente)
└── load-tests/             # scripts de carga con k6 (sección 9, pendiente)
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

## Progreso del laboratorio

- [x] Sección 4 — Proyecto base Spring Boot
- [x] Sección 5 — Pruebas unitarias con JUnit y Mockito (código base; falta Actividad 1)
- [x] Sección 6 — Pruebas de API con MockMvc (código base; falta Actividad 2)
- [x] Sección 7 — Pruebas de integración + dependencias Testcontainers (falta Actividad 3)
- [ ] Sección 8 — Pruebas E2E de frontend con Playwright
- [ ] Sección 9 — Pruebas de carga con k6
- [ ] Sección 10 — Estrategia de pruebas en CI/CD
- [ ] Sección 11 — Actividad integradora y reto final
