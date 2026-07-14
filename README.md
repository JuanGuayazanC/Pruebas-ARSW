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

## Progreso del laboratorio

- [x] Sección 4 — Proyecto base Spring Boot
- [ ] Sección 5 — Pruebas unitarias con JUnit y Mockito
- [ ] Sección 6 — Pruebas de API con MockMvc
- [ ] Sección 7 — Pruebas de integración con Testcontainers
- [ ] Sección 8 — Pruebas E2E de frontend con Playwright
- [ ] Sección 9 — Pruebas de carga con k6
- [ ] Sección 10 — Estrategia de pruebas en CI/CD
- [ ] Sección 11 — Actividad integradora y reto final
