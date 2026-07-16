# Pruebas ARSW — Comprehensive Testing Strategy

Lab for the *Software Architecture (ARSW)* course. It builds an order API
(`Order API`) in Spring Boot and applies, layer by layer, different types of
tests: unit, API, integration, frontend end-to-end and load testing, closing
with a CI/CD execution strategy.

This README documents the lab's general theory and will keep growing as each
section is completed.

## Central idea

Testing is not just about checking that a feature responds. Testing is about
building **quality evidence** about behavior, integration, performance, user
experience and system reliability. A good testing strategy protects quality
attributes such as reliability, maintainability, performance, security,
availability and evolvability.

## Layered testing strategy

```
Unit tests
      ↓
API tests
      ↓
Integration tests
      ↓
Frontend end-to-end tests
      ↓
Load tests
      ↓
Validation pipeline (CI/CD)
```

Each layer has a different purpose, cost and confidence level: the lower in the
pyramid, the faster and cheaper the test, but the less "realistic" the scenario
it validates; the higher up, the more expensive and slower, but the closer to
the system's real behavior.

| Test type | What it validates | Tools |
|---|---|---|
| Unit | Logic of an isolated class or function | JUnit, Mockito |
| API | HTTP status codes, JSON, validations, endpoint contract | MockMvc, REST Assured |
| Integration | Interaction between service, repository and database | `@SpringBootTest`, Testcontainers |
| Frontend automation | Critical flows from the user's perspective | Playwright, Cypress, Testing Library |
| Load | Behavior under multiple concurrent users/requests | k6, JMeter, Gatling |
| Pipeline | Repeatable execution to prevent regressions | GitHub Actions, GitLab CI, Jenkins |

**Guide recommendation:** not every test should run on every commit. Fast
tests (unit, API) run frequently; expensive ones (full integration, E2E, load)
are reserved for pull requests, releases or controlled environments.

## Repository structure

```
Pruebas-ARSW/
├── README.md                          # this file: general theory + execution guide
├── .github/workflows/                 # CI pipeline (section 10)
│   └── arsw-testing-pipeline.yml
└── order-api/                         # Spring Boot backend (Order API)
    ├── pom.xml
    ├── src/main/java/edu/eci/arsw/testing/...
    └── src/test/java/edu/eci/arsw/testing/...
```

> The `frontend-tests/` (Playwright, section 8) and `load-tests/` (k6, section
> 9) folders are added on the working branch for that part of the lab.

## Order API — base project (section 4)

A simple order API, just enough to apply the different types of tests without
building a full e-commerce application.

```
Client / Frontend
      ↓
Order API (Spring Boot)
      ↓
Order Service
      ↓
Order Repository
      ↓
Database (in-memory H2)
```

**Stack:** Java 17+ (Java 23 was used locally), Maven, Spring Boot 4.1.0,
Spring Web (`webmvc` starter in Boot 4), Spring Data JPA, Validation, H2
Database, Spring Boot Test.

> **Note on Spring Boot 4:** starting with Boot 4, starters were split into
> smaller modules (e.g. `spring-boot-starter-webmvc` instead of
> `spring-boot-starter-web`, and module-specific test starters such as
> `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`).
> JUnit 5 and Mockito still arrive transitively through those test starters,
> just as they used to via `spring-boot-starter-test`.

### Packages

- `model` — JPA entity `Order`.
- `dto` — `CreateOrderRequest` (input, with `@NotBlank`/`@Min` validations) and
  `OrderResponse` (output).
- `repository` — `OrderRepository`, extends `JpaRepository<Order, String>`.
- `service` — `OrderService`, business rule: rejects orders with
  `total > 5,000,000`.
- `controller` — `OrderController`, exposes `POST /orders` and
  `GET /orders/{id}`.

### How to run

From `order-api/`:

```bash
# compile
mvn compile

# run the application (port 8080 by default)
mvn spring-boot:run

# run the tests
mvn test
```

### Endpoints

| Method | Route | Description |
|---|---|---|
| `POST` | `/orders` | Creates an order. Body: `{ "customerId": "...", "total": 120000 }` |
| `GET` | `/orders/{id}` | Retrieves an order by id |

## Section 5 — Unit tests with JUnit and Mockito

`OrderServiceTest` tests `OrderService` **in isolation**, mocking
`OrderRepository` with Mockito (`mock(OrderRepository.class)`). No Spring
context is started: this is the fastest, cheapest test in the pyramid.

- `shouldCreateOrderWhenRequestIsValid`: configures `repository.save(...)`
  with `when(...).thenReturn(...)` and validates that the response has the
  expected data, and that `save` was invoked exactly once
  (`verify(repository, times(1))`).
- `shouldRejectOrderWhenTotalExceedsLimit`: uses `assertThrows` to validate
  that a total above 5,000,000 throws `IllegalArgumentException`, and that
  `save` is **never** called (`verify(repository, never())`) — confirming the
  business rule short-circuits the flow before touching the repository.

Run only this class: `mvn -Dtest=OrderServiceTest test`

> **Pending (guide Activity 1):** add tests for `findById` — one that returns
> an existing order and another that throws an exception when the order does
> not exist.

## Section 6 — API tests with MockMvc

`OrderControllerTest` tests the web layer in isolation with
`@WebMvcTest(OrderController.class)`: Spring only starts `OrderController` and
its associated `MockMvc`, **without** a database or the rest of the context.
The `OrderService` dependency is replaced with `@MockitoBean`.

- `shouldCreateOrder`: performs `POST /orders` with a valid JSON body and
  validates `201 Created` plus the response body via `jsonPath`.
- `shouldRejectInvalidRequest`: sends an empty `customerId` and a negative
  `total`; since the DTO uses `@NotBlank`/`@Min(1)` and the controller has
  `@Valid`, Spring responds `400 Bad Request` automatically, without the
  controller code having to validate it manually.

> **Spring Boot 4 note:** `@WebMvcTest` moved packages compared to Boot 3 — it
> is now `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`
> instead of `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest`.
> The rest of the MockMvc classes (`MockMvc`, `MockMvcRequestBuilders`,
> `MockMvcResultMatchers`) remain in Spring Framework
> (`org.springframework.test.web.servlet.*`) and did not change.

Run only this class: `mvn -Dtest=OrderControllerTest test`

> **Pending (guide Activity 2):** add a test for `GET /orders/{id}` that
> validates `200 OK`, the order's `id`, `customerId` and `status`.

## Section 7 — Integration tests

`OrderIntegrationTest` uses `@SpringBootTest`, which starts the **full**
Spring context (real service + real repository + real in-memory H2 database,
no mocks). `shouldCreateAndFindOrder` creates an order through the real
`OrderService` and then looks it up by id, verifying both objects match. It's
the slowest and most expensive test of the three, but the one that gives the
most confidence because it exercises the real interaction between layers.

Run only this class: `mvn -Dtest=OrderIntegrationTest test`

### 7.1 Testcontainers (dependencies)

Added to `pom.xml`: the `org.testcontainers:junit-jupiter` and
`org.testcontainers:postgresql` dependencies (`test` scope), plus the
`testcontainers-bom` in `dependencyManagement` to pin the version (`1.21.3`) —
the guide's snippet doesn't include the BOM, so without it Maven can't resolve
those dependencies' version. For now `OrderIntegrationTest` still uses the
default in-memory H2; using a real PostgreSQL container in the test would be a
future extension (replacing the `DataSource` with one backed by a
Testcontainers container).

> **Pending (guide Activity 3):** explain the difference between the
> service's unit test, the controller's MockMvc test, and the integration
> test with `@SpringBootTest`, analyzing speed, confidence and maintenance
> cost.

## Section 10 — Testing strategy in CI/CD

Not every test should run on every commit: fast ones (unit, API) should;
expensive ones (full integration, E2E, load) are reserved for pull requests,
releases or controlled environments.

`.github/workflows/arsw-testing-pipeline.yml` implements the guide's
`backend-tests` job: on every `push` to `main` and every `pull_request`, it
checks out the code, installs Java 17 (Temurin) and runs `mvn test`.

> **Adjustment to the guide's snippet:** the original `run: mvn test` assumes
> `pom.xml` is at the repo root. Here it lives in `order-api/`, so
> `working-directory: order-api` was added to the step so the pipeline
> actually finds the project.

This pipeline is shared lab infrastructure (it doesn't belong to a single
person's section of work), which is why it lives directly on `develop`.

## Lab progress

- [x] Section 4 — Base Spring Boot project
- [x] Section 5 — Unit tests with JUnit and Mockito (base code; Activity 1 pending)
- [x] Section 6 — API tests with MockMvc (base code; Activity 2 pending)
- [x] Section 7 — Integration tests + Testcontainers dependencies (Activity 3 pending)
- [x] Section 10 — GitHub Actions pipeline for backend tests
- [ ] Section 8 — Frontend E2E tests with Playwright (on that part's working branch)
- [ ] Section 9 — Load tests with k6 (on that part's working branch)
- [ ] Section 11 — Integrative activity and final challenge
