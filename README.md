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
├── order-api/                         # Spring Boot backend (Order API)
│   ├── pom.xml
│   ├── src/main/java/edu/eci/arsw/testing/...
│   └── src/test/java/edu/eci/arsw/testing/...
├── frontend-tests/                    # Playwright E2E tests (section 8)
│   └── tests/orders.spec.js
└── load-tests/                        # k6 load scripts (section 9)
    ├── load-test.js
    └── load-test-stages.js
```

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

- `shouldReturnOrderWhenIdExists` (Activity 1): mocks
  `repository.findById("ORD-1")` to return `Optional.of(order)` and validates
  that `OrderResponse` has the same data as the mocked entity.
- `shouldThrowExceptionWhenOrderNotFound` (Activity 1): mocks
  `repository.findById(...)` to return `Optional.empty()` and uses
  `assertThrows` to validate that `findById` throws
  `IllegalArgumentException` — covering the branch of `orElseThrow` that
  actually fires.

Run only this class: `mvn -Dtest=OrderServiceTest test`

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

- `shouldFindOrderById` (Activity 2): mocks `service.findById("ORD-1")` and
  performs `GET /orders/ORD-1`, validating `200 OK` and the `id`,
  `customerId` and `status` in the response body.

Run only this class: `mvn -Dtest=OrderControllerTest test`

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

### Activity 3 — Unit vs. MockMvc vs. Integration

| | Unit (`OrderServiceTest`) | API/MockMvc (`OrderControllerTest`) | Integration (`OrderIntegrationTest`) |
|---|---|---|---|
| **What it starts** | No Spring at all; just the `OrderService` object with a mocked `OrderRepository` | Only the web layer (`@WebMvcTest`): the `DispatcherServlet`, `OrderController` and JSON serialization, with `OrderService` mocked | The full Spring context: controller, service, repository and a real H2 database |
| **Speed** | Fastest (ms) — no context startup or I/O | Intermediate — starts a partial Spring context, slower than unit but much lighter than a full one | Slowest — starts the whole context, configures JPA/Hibernate and a real database connection |
| **Confidence** | Low/medium — tests business logic in isolation, but doesn't guarantee the controller, serialization or repository actually work together | Medium — guarantees routes, HTTP status codes, validations (`@Valid`) and input/output JSON are correct, but `OrderService` is still fake | High — the only one that proves the real pieces (service + repository + JPA + database) actually integrate and produce the expected result |
| **Maintenance cost** | Low — mocking the only dependency means database or Spring changes don't break this test; it only breaks if the `OrderService`/`OrderRepository` contract changes | Medium — can break due to route, DTO or validation rule changes, though it doesn't depend on the database | High — more fragile to configuration changes (datasource properties, JPA mapping, Spring Boot version), and more expensive to debug when it fails because more pieces are involved |

**Conclusion:** none replaces the others — each one catches a different kind of error (business logic vs. HTTP contract vs. real integration), which is why the guide organizes them as a pyramid: many cheap unit tests at the base, fewer expensive integration tests at the top. An ideal change is first validated with the unit test (immediate feedback) and confirmed with the integration test before merging.

## Section 8 — Automated frontend tests with Playwright

Playwright automates a real browser to validate complete flows **from the
user's perspective** (end-to-end): it opens the page, fills forms, clicks and
verifies what's on screen. It's the slowest, most expensive layer before load
tests, but the one that most closely resembles what a real user does.

`frontend-tests/tests/orders.spec.js` contains two example tests from the
guide:

- Verifies that the main page loads with an expected title.
- Simulates creating an order by filling `customer-id` and `order-total`,
  clicking `create-order`, and checking that `order-status` shows `CREATED`.

**Important — this is not runnable as-is yet:** the test points to
`http://localhost:5173` (Vite's typical port) and to `data-testid` selectors,
but **this repository has no frontend implementation** — the guide doesn't ask
for one to be built, it just assumes one exists. That's why the final
challenge (section 11) says *"propose or implement"* an E2E test: proposing it
already meets the learning objective.

For these selectors to ever work, frontend components should include
`data-testid` attributes (avoids depending on text or styles that change
often):

```html
<input data-testid="customer-id" />
<input data-testid="order-total" />
<button data-testid="create-order">Create order</button>
<div data-testid="order-status"></div>
```

`npm init playwright@latest` (section 8.1) was not run: it's an interactive
scaffolding tool that downloads browser binaries (Chromium/Firefox/WebKit,
several hundred MB). When you have a real frontend to test against:

```bash
cd frontend-tests
npm init playwright@latest
npx playwright test
npx playwright show-report
```

### Activity 4 — Design of three E2E tests

The repository has no runnable frontend application, so these tests are
presented as a functional design for a future React interface. The goal is to
validate the critical flows from the browser, using stable `data-testid`
selectors and verifying the result visible to the user.

| Test | Flow | Input data | Expected result |
|---|---|---|---|
| Successfully create an order | Open the orders screen, fill in the form and select **Create order**. | `customerId = CUS-E2E-01`, `total = 120000`. | The UI shows a confirmation, a generated id and `CREATED` status; the underlying HTTP response is `201 Created`. |
| Reject an invalid total | Open the form, enter a total below 1 and submit it. | `customerId = CUS-E2E-INVALID`, `total = -10`. | The UI shows a validation error, does not show a created order, and the API responds `400 Bad Request`; no valid order should be sent to the service. |
| Look up an order by id | Create or select an existing order, copy its id, open the lookup screen, enter the id and select **Search**. | `orderId` returned by the first test, e.g. `ORD-...`. | The UI shows the correct order with the same id, `customerId` and `CREATED` status; the API responds `200 OK`. |

Proposed automation flow for the selectors: `[data-testid="customer-id"]`,
`[data-testid="order-total"]`, `[data-testid="create-order"]`,
`[data-testid="order-id"]`, `[data-testid="order-status"]`,
`[data-testid="order-search-id"]` and `[data-testid="find-order"]`. The third
test must reuse the id captured from the response or the UI, never a fixed id
that might not exist in the database.

As an additional criterion, each test should clean up or isolate its data so
it doesn't depend on execution order. Once a real frontend exists, this design
can be turned into a runnable Playwright test, adding a `webServer` to start
the frontend and the API during the test.

## Section 9 — Load tests with k6

Load tests validate how the system behaves under multiple concurrent users or
requests: latency, throughput, error rate and degradation. Unlike the layers
above, here **how many** requests fire and over how much time actually
matters, not just whether the response is correct.

- `load-tests/load-test.js`: simple, constant load — 10 *virtual users*
  (`vus`) for 30s, issuing `GET /orders/{id}` and checking for `status 200`
  and `< 500ms` response time. The id comes from `ORDER_ID` and defaults to
  `ORD-1` to keep compatibility with the guide.
- `load-tests/load-test-stages.js`: variable load via *stages* (ramp 0→10 over
  20s, 10→30 over 30s, 30→0 over 20s) against `POST /orders`, with
  **thresholds**: the test fails if the error rate exceeds 5% or the p95
  latency exceeds 800ms. `__VU` is the current virtual user id, used to
  generate a different `customerId` per user. Both scripts accept `BASE_URL`;
  they default to `http://localhost:8080`.

To run, with `order-api` running on `localhost:8080`
(`mvn spring-boot:run` from `order-api/`):

```bash
# install (pick based on OS)
winget install k6            # Windows
brew install k6               # macOS
docker run --rm -i grafana/k6 run - < load-test.js   # Docker, no install needed

# run
cd load-tests
k6 run load-test.js --env ORDER_ID=ORD-<existing-id>
k6 run load-test-stages.js
```

To get the id the basic test needs, first create an order against the API and
pass the returned id to k6. In PowerShell:

```powershell
$order = Invoke-RestMethod -Method Post -Uri http://localhost:8080/orders `
  -ContentType 'application/json' `
  -Body '{"customerId":"CUS-LOAD-GET","total":120000}'
k6 run load-test.js --env ORDER_ID=$order.id
```

### Activity 5 — Execution and analysis

The run was performed locally against Spring Boot, using Java 21, Maven
3.9.12, in-memory H2 and k6 on Windows. The API must stay up on
`http://localhost:8080` during both tests; no PostgreSQL, Docker, AWS or
external deployment is required for this lab. The load test runs from a
controlled environment and is not wired into the per-commit pipeline because
it takes longer and could interfere with other jobs; it's a candidate for a
manual run or a pre-release step.

| Script | VUs | Duration/config | Requests | Failures | p95 | Thresholds | Result |
|---|---:|---|---:|---:|---:|---|---|
| `load-test.js` | 10 | 30 s constant | 300 | 0.00% (0/300) | 18.11 ms | Not configured; checks 100%: HTTP 200 and < 500 ms | Successful |
| `load-test-stages.js` | 0→10→30→0 (max. 30) | 20 s + 30 s + 20 s = 70 s | 1004 | 0.00% (0/1004) | 9.87 ms | `http_req_failed < 5%`: passed; `http_req_duration p(95) < 800 ms`: passed | Successful |

Request counts and metrics are filled in directly from k6's summary output for
each run. The conclusion should take into account that this result is a
single-instance local baseline with in-memory H2: it's useful for catching
regressions and comparing changes, but it doesn't represent production
capacity. For a deployed environment, `BASE_URL` can be used, e.g.:
`k6 run load-test-stages.js --env BASE_URL=https://api.example.com`.

**Technical conclusion:** the API responded steadily in the local environment
under the evaluated loads. The constant-load test processed 300 requests with
no errors, and the staged-load test processed 1004 requests without exceeding
the thresholds; in both cases p95 stayed well under the configured limit or,
for the basic test, the 500ms check. These results do not support claims about
production capacity: an external database, real network between services,
authentication, observability and a load representative of the target
deployment are all missing.

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

## Section 11 — Integrative activity and final challenge

### 11.1 Integrative activity: testing strategy for an e-commerce app

Scenario: React frontend, Spring Boot backend, PostgreSQL database, REST API,
authentication and AWS deployment. Unlike the lab's Order API (in-memory H2, no
authentication, no real frontend or deployment), this scenario adds three new
sources of risk: a real database with its own behavior, a security perimeter to
protect, and a frontend/infrastructure that actually gets deployed. That's
exactly what changes the most in the testing pyramid: the **integration** layer
(H2 is no longer enough — testing against real Postgres with Testcontainers,
including migrations, becomes necessary) and a new **security** layer appears
that the lab never needed because the Order API has no authentication.

| Test type | Tools | Layer it validates | Pipeline stage | Errors it detects | Evidence it generates |
|---|---|---|---|---|---|
| Unit | JUnit 5 + Mockito (backend); Vitest/Jest + Testing Library (frontend) | Isolated business logic: Spring services, React hooks/reducers | Every commit | Logic errors, incorrect calculations, broken validations | JUnit/Vitest reports, coverage |
| API / Contract | MockMvc or REST Assured; optionally Pact for contract testing with the frontend | REST endpoints: HTTP status codes, JSON schemas, role-based authorization | Every commit / PR | Contract changes that break the frontend, endpoints missing input validation | Test reports + versioned contract |
| Integration | `@SpringBootTest` + Testcontainers (real PostgreSQL in Docker) | Real service–repository–Postgres interaction, including migrations (Flyway/Liquibase) | Pull request | JPA mappings that only fail on Postgres (not H2), broken migrations, violated constraints | Execution logs, integration report |
| Security / authentication | Spring Security Test (`@WithMockUser`, test JWTs) | Protected endpoints reject unauthenticated or unauthorized users | Pull request | Unprotected endpoints, data leaks between users, privilege escalation | Security test report, endpoint × role matrix |
| Frontend E2E | Playwright/Cypress against a deployed staging environment | Real critical flows: login, catalog, cart, checkout, payment | Before release / nightly on staging | Regressions in the real React–API integration, broken critical business flows | Failure videos/screenshots, HTML report |
| Load | k6 against staging (not local) | Behavior under real concurrency: catalog, checkout, payment gateway | Before release, in a controlled environment | Bottlenecks, timeouts, database degradation under load | k6 report (p95, error rate, throughput), dashboards |
| Pipeline / CD | GitHub Actions + AWS deployment (ECS/CodeDeploy) | That build → test → deploy works without manual intervention | Every push/PR and before every release | Regressions not caught before production, broken deployments | Run history, status badges, artifacts |

### 11.2 Final challenge

Points 13 through 19 of the final challenge are already covered in earlier sections:
the unit test (13, section 5), the MockMvc API test (14, section 6), the integration
test (15, section 7), the proposed E2E test (16, section 8), the k6 scripts (17,
section 9), and running them with evidence plus the load metrics analysis (18 and 19,
section 9 — Activity 5). Points 20 and 21 remain.

#### Point 20 — Proposed testing pipeline

Extends section 10's table to cover every layer from the integrative activity:

```
Every commit (fast, minutes):
  - Backend + frontend compilation
  - Backend unit tests (JUnit/Mockito)
  - Frontend unit tests (Vitest/Jest)
  - Lint / type-check

Pull request:
  - Everything above, plus:
  - API tests (MockMvc)
  - Integration tests with Testcontainers (real Postgres)
  - Authentication/authorization tests
  - Static security analysis (vulnerable dependencies)

Before release / nightly (staging environment):
  - Full E2E suite with Playwright against staging
  - Load test with k6 against staging
  - Post-deploy smoke tests on staging

Production (post-deploy):
  - Minimal smoke test
  - Continuous monitoring and observability
```

The reasoning behind the order: each layer is slower and more expensive than the
previous one, so it's reserved for the moment where its cost is justified by the
risk it mitigates — running the full Playwright suite on every commit would be
too slow for fast developer feedback, but skipping it before a release would let
regressions in critical flows like checkout slip through.

#### Point 21 — Reflection: which tests add the most value?

There's no single answer — it depends on the cost of a bug reaching production
in that part of the system. Based on the evidence generated in this lab:

- **Unit** tests gave the fastest, cheapest feedback (ms), but on their own they
  wouldn't have caught, for example, that `@WebMvcTest` moved packages in Spring
  Boot 4 — only compiling/running the real API test revealed that.
- **Integration** gave the most confidence for the least writing effort: a
  single test (`shouldCreateAndFindOrder`) validated that four layers genuinely
  work together, something no unit test can claim.
- **Load** was the only one capable of revealing information no other test in
  the lab could: how the system behaves under 30 concurrent users, not just
  whether a single request is correct.

For a system like the e-commerce app in 11.1, the most value lies in the tests
that protect the flows with the highest cost if they fail in production:
**integration** (prevents a schema change from silently breaking order
persistence) and **security** (an authorization failure exposes other users'
data or enables fraudulent payments) are the highest priority, closely followed
by **checkout E2E** — the flow where a bug costs the business real money. Unit
tests remain the best cost/benefit ratio for fast day-to-day iteration, but
they're not the ones that protect the business most if only one type of test
could be kept.

## Lab progress

- [x] Section 4 — Base Spring Boot project
- [x] Section 5 — Unit tests with JUnit and Mockito (includes Activity 1)
- [x] Section 6 — API tests with MockMvc (includes Activity 2)
- [x] Section 7 — Integration tests + Testcontainers dependencies (includes Activity 3)
- [x] Section 8 — Example E2E test with Playwright (Activity 4 design; no real frontend implemented)
- [x] Section 9 — k6 load scripts (parameterized scripts + Activity 5 analysis)
- [x] Section 10 — GitHub Actions pipeline for backend tests
- [x] Section 11 — Integrative activity and final challenge
