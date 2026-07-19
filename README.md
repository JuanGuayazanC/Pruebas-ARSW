# *Laboratorio* Pruebas (Estrategia Integral de Pruebas para Aplicaciones Web y Microservicios)
Construye una API de pedidos (`Order API`) en Spring Boot y le aplica, capa por capa, distintos tipos de pruebas: unitarias, de API, de integración, end-to-end de frontend y de carga, cerrando con una estrategia de ejecución en CI/CD.

Este README documenta la teoría general del laboratorio y se irá completando a medida que se avanza en cada sección.

## Autor

JUAN SEBASTIÁN GUAYAZÁN CLAVIJO  
Arquitecturas de Software (ISIS ARSW - 101)  
Decanatura de Ingeniería de Sistemas  
Ingeniería de Sistemas  
Escuela Colombiana de Ingeniería Julio Garavito  
2026-i

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

## Cómo ejecutar

Clona el repositorio, ubícate en esta rama y entra a `order-api/`:

```bash
git clone https://github.com/JuanGuayazanC/Pruebas-ARSW.git
cd Pruebas-ARSW
git checkout develop-es
cd order-api
```

Desde `order-api/`:

```bash
# compilar
mvn compile

# ejecutar la aplicación (por defecto en el puerto 8080)
mvn spring-boot:run

# ejecutar las pruebas
mvn test
```

> `frontend-tests/` (Playwright) y `load-tests/` (k6) son subcarpetas independientes
> con su propia configuración; ambas requieren la Order API en ejecución y están
> documentadas en las secciones 8 y 9.

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

### Actividad 4 — Diseño de tres pruebas E2E

El repositorio no contiene una aplicación frontend ejecutable, así que estas pruebas
se presentan como diseño funcional para una futura interfaz React. El objetivo es
validar los flujos críticos desde el navegador, usando selectores estables
`data-testid` y verificando el resultado visible para el usuario.

| Prueba | Flujo | Datos de entrada | Resultado esperado |
|---|---|---|---|
| Crear pedido exitosamente | Abrir la pantalla de pedidos, diligenciar el formulario y seleccionar **Crear pedido**. | `customerId = CUS-E2E-01`, `total = 120000`. | La interfaz muestra confirmación, un id generado y estado `CREATED`; la respuesta HTTP subyacente es `201 Created`. |
| Rechazar total inválido | Abrir el formulario, ingresar un total menor que 1 y enviarlo. | `customerId = CUS-E2E-INVALID`, `total = -10`. | La interfaz muestra un error de validación, no muestra un pedido creado y la API responde `400 Bad Request`; no se debe enviar una orden válida al servicio. |
| Consultar pedido por id | Crear o seleccionar un pedido existente, copiar su id, abrir la consulta, ingresar el id y seleccionar **Consultar**. | `orderId` devuelto por la primera prueba, por ejemplo `ORD-...`. | La interfaz muestra el pedido correcto con el mismo id, `customerId` y estado `CREATED`; la API responde `200 OK`. |

Flujo de automatización propuesto para los selectores: `[data-testid="customer-id"]`,
`[data-testid="order-total"]`, `[data-testid="create-order"]`,
`[data-testid="order-id"]`, `[data-testid="order-status"]`,
`[data-testid="order-search-id"]` y `[data-testid="find-order"]`. La tercera prueba
debe reutilizar el id capturado de la respuesta o de la interfaz, nunca un id fijo que
pueda no existir en la base de datos.

Como criterio adicional, cada prueba debe limpiar o aislar sus datos para no depender
del orden de ejecución. Cuando exista el frontend real, se puede convertir este diseño
en Playwright ejecutable y añadir un `webServer` para levantar frontend y API durante
la prueba.

## Sección 9 — Pruebas de carga con k6

Las pruebas de carga validan cómo se comporta el sistema bajo múltiples usuarios o
solicitudes concurrentes: latencia, throughput, tasa de error y degradación. A
diferencia de las capas anteriores, aquí sí importa **cuántas** peticiones se
disparan y en cuánto tiempo, no solo si la respuesta es correcta.

- `load-tests/load-test.js`: carga simple y constante — 10 *usuarios virtuales*
  (`vus`) durante 30s, haciendo `GET /orders/{id}` y verificando `status 200` y
  `< 500ms` de respuesta. El id se recibe desde `ORDER_ID` y por defecto conserva
  `ORD-1` para mantener compatibilidad con la guía.
- `load-tests/load-test-stages.js`: carga variable por *stages* (rampa de 0→10 en
  20s, 10→30 en 30s, 30→0 en 20s) contra `POST /orders`, con **thresholds**: la
  prueba falla si la tasa de error supera 5% o si el p95 de latencia supera 800ms.
  `__VU` es el id del usuario virtual actual, usado para generar un `customerId`
  distinto por usuario. Ambos scripts aceptan `BASE_URL`; por defecto usan
  `http://localhost:8080`.

Para ejecutar, con la `order-api` corriendo en `localhost:8080`
(`mvn spring-boot:run` desde `order-api/`):

```bash
# instalar (elegir según el SO)
winget install k6            # Windows
brew install k6               # macOS
docker run --rm -i grafana/k6 run - < load-test.js   # Docker, sin instalar nada

# ejecutar
cd load-tests
k6 run load-test.js --env ORDER_ID=ORD-<id-existente>
k6 run load-test-stages.js
```

Para obtener el id que necesita la prueba básica, primero se crea un pedido contra la
API y se pasa el id devuelto a k6. En PowerShell:

```powershell
$order = Invoke-RestMethod -Method Post -Uri http://localhost:8080/orders `
  -ContentType 'application/json' `
  -Body '{"customerId":"CUS-LOAD-GET","total":120000}'
k6 run load-test.js --env ORDER_ID=$order.id
```

### Actividad 5 — Ejecución y análisis

La ejecución se realizó localmente contra Spring Boot, usando Java 21, Maven 3.9.12,
H2 en memoria y k6 en Windows. La API debe permanecer levantada en
`http://localhost:8080` durante las dos pruebas; no se requiere PostgreSQL, Docker,
AWS ni un despliegue externo para este laboratorio. La carga se ejecuta desde un
ambiente controlado y no se incorpora al pipeline de cada commit porque dura más y
puede interferir con otros jobs; es candidata para una ejecución manual o previa a
release.

| Script | VUs | Duración/configuración | Solicitudes | Fallos | p95 | Thresholds | Resultado |
|---|---:|---|---:|---:|---:|---|---|
| `load-test.js` | 10 | 30 s constantes | 300 | 0.00% (0/300) | 18.11 ms | No configurados; checks 100%: HTTP 200 y < 500 ms | Exitoso |
| `load-test-stages.js` | 0→10→30→0 (máx. 30) | 20 s + 30 s + 20 s = 70 s | 1004 | 0.00% (0/1004) | 9.87 ms | `http_req_failed < 5%`: aprobado; `http_req_duration p(95) < 800 ms`: aprobado | Exitoso |

La cantidad de solicitudes y las métricas se completan directamente con el resumen
emitido por k6 para cada ejecución. La conclusión debe considerar que este resultado
es una línea base de una sola instancia local con H2 en memoria: sirve para detectar
regresiones y comparar cambios, pero no representa la capacidad de producción. Para
un ambiente desplegado, se puede usar `BASE_URL`, por ejemplo:
`k6 run load-test-stages.js --env BASE_URL=https://api.example.com`.

**Conclusión técnica de la ejecución:** la API respondió de forma estable en el
ambiente local bajo las cargas evaluadas. La prueba constante procesó 300 solicitudes
sin errores y la prueba por etapas procesó 1004 solicitudes sin superar los thresholds;
en ambos casos el p95 quedó muy por debajo del límite configurado o, para la prueba
básica, del check de 500 ms. Estos resultados no permiten afirmar capacidad de
producción: faltan una base de datos externa, red entre servicios, autenticación,
observabilidad y una prueba con una carga representativa del despliegue objetivo.

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

## Sección 11 — Actividad integradora y reto final

### 11.1 Actividad integradora: estrategia de pruebas para un e-commerce

Escenario: frontend React, backend Spring Boot, base de datos PostgreSQL, API REST,
autenticación y despliegue en AWS. A diferencia del Order API del laboratorio (H2 en
memoria, sin autenticación, sin frontend ni despliegue reales), este escenario agrega
tres fuentes nuevas de riesgo: una base de datos real con su propio comportamiento,
un perímetro de seguridad que proteger, y un frontend/infraestructura que sí se
despliegan de verdad. Eso es justamente lo que más cambia en la pirámide de pruebas:
la capa de **integración** (ya no basta con H2, hay que probar contra Postgres real
con Testcontainers, incluyendo migraciones) y aparece una capa nueva de **seguridad**
que el laboratorio no necesitó porque el Order API no tiene autenticación.

| Tipo de prueba | Herramientas | Capa que valida | Momento en el pipeline | Errores que detecta | Evidencia que genera |
|---|---|---|---|---|---|
| Unitaria | JUnit 5 + Mockito (backend); Vitest/Jest + Testing Library (frontend) | Lógica de negocio aislada: servicios Spring, hooks/reducers de React | Cada commit | Errores de lógica, cálculos incorrectos, validaciones rotas | Reporte JUnit/Vitest, cobertura |
| API / Contrato | MockMvc o REST Assured; opcionalmente Pact para contract testing con el frontend | Endpoints REST: códigos HTTP, esquemas JSON, autorización por rol | Cada commit / PR | Cambios de contrato que rompen al frontend, endpoints sin validar entrada | Reportes de test + contrato versionado |
| Integración | `@SpringBootTest` + Testcontainers (PostgreSQL real en Docker) | Interacción real servicio–repositorio–Postgres, incluyendo migraciones (Flyway/Liquibase) | Pull request | Mapeos JPA que fallan solo en Postgres (no en H2), migraciones rotas, constraints violados | Logs de ejecución, reporte de integración |
| Seguridad / autenticación | Spring Security Test (`@WithMockUser`, JWT de prueba) | Que los endpoints protegidos rechacen usuarios no autenticados o sin permiso | Pull request | Endpoints desprotegidos, fuga de datos entre usuarios, escalado de privilegios | Reporte de pruebas de seguridad, matriz endpoint × rol |
| Frontend E2E | Playwright/Cypress contra un ambiente de staging desplegado | Flujos críticos reales: login, catálogo, carrito, checkout, pago | Antes de release / nightly en staging | Regresiones en la integración React–API real, fallos de flujos críticos de negocio | Video/screenshots de fallos, reporte HTML |
| Carga | k6 contra staging (no contra local) | Comportamiento bajo concurrencia real: catálogo, checkout, pasarela de pago | Antes de release, en ambiente controlado | Cuellos de botella, timeouts, degradación de la base de datos bajo carga | Reporte k6 (p95, error rate, throughput), dashboards |
| Pipeline / CD | GitHub Actions + despliegue en AWS (ECS/CodeDeploy) | Que build → test → deploy funcione sin intervención manual | Cada push/PR y antes de cada release | Regresiones no detectadas antes de producción, despliegues rotos | Historial de ejecuciones, badges de estado, artefactos |

### 11.2 Reto final

Los puntos 13 a 19 del reto final ya quedaron cubiertos en secciones anteriores: la
prueba unitaria (13, sección 5), la prueba de API con MockMvc (14, sección 6), la
prueba de integración (15, sección 7), la propuesta de prueba E2E (16, sección 8), los
scripts k6 (17, sección 9), y la ejecución con evidencia y el análisis de métricas de
carga (18 y 19, sección 9 — Actividad 5). Faltan los puntos 20 y 21.

#### Punto 20 — Pipeline de pruebas propuesto

Extiende la tabla de la sección 10, cubriendo todas las capas de la actividad
integradora:

```
Cada commit (rápido, minutos):
  - Compilación backend + frontend
  - Pruebas unitarias backend (JUnit/Mockito)
  - Pruebas unitarias frontend (Vitest/Jest)
  - Lint / type-check

Pull request:
  - Todo lo anterior, más:
  - Pruebas de API (MockMvc)
  - Pruebas de integración con Testcontainers (Postgres real)
  - Pruebas de autenticación/autorización
  - Análisis estático de seguridad (dependencias vulnerables)

Antes de release / nightly (ambiente de staging):
  - Suite E2E completa con Playwright contra staging
  - Prueba de carga con k6 contra staging
  - Smoke tests post-despliegue en staging

Producción (post-deploy):
  - Smoke test mínimo
  - Monitoreo y observabilidad continuos
```

La razón del orden: cada capa es más lenta y cara que la anterior, así que se reserva
para el momento donde el costo de ejecutarla se justifica por el riesgo que mitiga —
correr Playwright completo en cada commit sería demasiado lento para dar feedback
rápido al desarrollador, pero omitirlo antes de un release dejaría pasar regresiones
de flujos críticos como el checkout.

#### Punto 21 — Reflexión: ¿qué pruebas aportan más valor?

No hay una respuesta única — depende del costo de que un error llegue a producción en
esa parte del sistema. Con la evidencia que generamos en este laboratorio:

- Las **unitarias** dieron el feedback más rápido y barato (ms), pero por sí solas no
  hubieran detectado, por ejemplo, que `@WebMvcTest` cambió de paquete en Spring Boot
  4 — eso solo lo reveló compilar/ejecutar la prueba de API real.
- La de **integración** fue la que más confianza dio con menor esfuerzo de escritura:
  un solo test (`shouldCreateAndFindOrder`) validó que cuatro capas trabajan juntas de
  verdad, algo que ninguna prueba unitaria puede afirmar.
- La de **carga** fue la única capaz de revelar información que ninguna otra prueba
  del laboratorio podía dar: cómo se comporta el sistema bajo 30 usuarios concurrentes,
  no solo si una solicitud individual es correcta.

Para un sistema como el e-commerce del punto 11.1, el mayor valor está en las pruebas
que protegen los flujos con más costo si fallan en producción: **integración** (evita
que un cambio de esquema rompa silenciosamente el guardado de pedidos) y **seguridad**
(un fallo de autorización expone datos de otros usuarios o permite pagos fraudulentos)
son las de mayor prioridad, seguidas de cerca por **E2E del checkout** — es el flujo
donde un bug le cuesta dinero real al negocio. Las unitarias siguen siendo las de mejor
relación costo/beneficio para iterar rápido día a día, pero no son las que más protegen
al negocio si solo se pudiera mantener un tipo de prueba.
