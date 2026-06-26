# ticket-triage

A production-shaped Spring Boot 3.3 / Java 17 service that triages IT incident
tickets. A ticket is accepted immediately and classified **asynchronously** into
a severity (`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`), routed to a suggested queue, and
given a rationale. Triage is pluggable: an offline rule-based classifier (default)
or an LLM-backed client calling an Anthropic-style `/v1/messages` endpoint.

[![CI](https://github.com/LaithAskar/ticket-triage/actions/workflows/ci.yml/badge.svg)](https://github.com/LaithAskar/ticket-triage/actions/workflows/ci.yml)

## Run it in your browser (no local setup)

The easiest way — nothing to install:

1. On the GitHub repo, click **Code ▸ Codespaces ▸ Create codespace on main**.
2. Wait for it to build (it runs `./mvnw package` automatically), then in the terminal run:
   ```bash
   ./mvnw spring-boot:run
   ```
3. When the **8080** port notification appears, click **Open in Browser**, then add `/swagger-ui.html` to the URL. Click **Authorize**, paste `local-dev-key`, and try the endpoints.

Locally instead? You only need **JDK 17** (no Maven — the wrapper handles it):
```bash
./mvnw spring-boot:run            # macOS/Linux
.\mvnw.cmd spring-boot:run        # Windows PowerShell
```


## Highlights

- **Layered architecture** — controller → service → repository, with DTO records separate from the JPA entity.
- **Asynchronous triage** — `POST` returns `202 Accepted`; classification runs after the transaction commits, via a `@TransactionalEventListener(AFTER_COMMIT)` on a bounded executor (no request thread blocked, no read-before-commit race).
- **Pluggable strategy** — `TriageClient` selected by `@ConditionalOnProperty(triage.mode)`: `StubTriageClient` (offline) or `LlmTriageClient` (WebClient).
- **Resilient LLM calls** — per-request timeout + bounded exponential-backoff retry on transient 5xx; graceful `MEDIUM` fallback (never fails a ticket because the model is down).
- **Security** — stateless API-key auth (`X-API-Key`) with a constant-time check and JSON 401s.
- **Persistence** — Spring Data JPA, Flyway migrations, `ddl-auto=validate`. H2 for local/dev/test, PostgreSQL via the `postgres` profile.
- **Observability** — Actuator (`health`, `info`, `prometheus`) and a custom `tickets.triaged` metric tagged by status and severity.
- **API docs** — OpenAPI 3 + Swagger UI, with the API-key scheme wired in.
- **Tested at every layer** — classifier matrix, WebClient (`MockWebServer`), service, async listener, controller slice, and a full `@SpringBootTest` end-to-end test.
- **Ops** — multi-stage Dockerfile (non-root, healthcheck), docker-compose with Postgres, GitHub Actions CI.

## Architecture

```
HTTP → TicketController (@RestController, @Valid, OpenAPI)
          → TicketService (@Service, @Transactional)
                ├── TicketRepository (Spring Data JPA → H2 / PostgreSQL)
                └── publishes TicketCreatedEvent
                        │  (AFTER_COMMIT, @Async)
                        ▼
                  TriageEventListener
                        → TriageClient (interface)
                              ├── StubTriageClient  (triage.mode=stub, default — keyword rules, no network)
                              └── LlmTriageClient    (triage.mode=llm — WebClient → /v1/messages, retry + fallback)
                        → updates Ticket (COMPLETED / FAILED) + Micrometer metric

Cross-cutting: SecurityConfig (API-key filter), GlobalExceptionHandler (@RestControllerAdvice),
               Actuator, OpenAPI/Swagger, Flyway.
```

## Requirements

- JDK 17, Maven 3.9+
- (Optional) Docker for the containerized / PostgreSQL path

## Run

```bash
# from ticket-triage/
mvn spring-boot:run
```

- Service: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`
- H2 console: `http://localhost:8080/h2-console` (JDBC `jdbc:h2:mem:tickets`, user `sa`, no password)

The default API key is `local-dev-key` (override with the `API_KEY` env var).

### With PostgreSQL (Docker)

```bash
docker compose up --build
```

### LLM triage mode

```bash
# PowerShell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
mvn spring-boot:run "-Dspring-boot.run.arguments=--triage.mode=llm"
```

## API

All `/api/**` endpoints require the `X-API-Key` header.

### Create + triage — `POST /api/tickets` → `202 Accepted`

```bash
curl -i -X POST http://localhost:8080/api/tickets \
  -H "Content-Type: application/json" \
  -H "X-API-Key: local-dev-key" \
  -d '{
        "subject": "Production database is down",
        "body": "Users cannot place orders; the primary DB is unreachable and timing out."
      }'
```

Response (`Location: /api/tickets/1`) — note `triageStatus: PENDING`, severity not yet set:

```json
{
  "id": 1,
  "subject": "Production database is down",
  "body": "Users cannot place orders; the primary DB is unreachable and timing out.",
  "triageStatus": "PENDING",
  "severity": null,
  "suggestedQueue": null,
  "rationale": null,
  "createdAt": "2026-06-25T12:00:00Z"
}
```

A moment later the triage has completed:

```bash
curl http://localhost:8080/api/tickets/1 -H "X-API-Key: local-dev-key"
```

```json
{
  "id": 1,
  "triageStatus": "COMPLETED",
  "severity": "CRITICAL",
  "suggestedQueue": "Database",
  "rationale": "Rule-based triage: classified CRITICAL and routed to 'Database' based on keyword analysis.",
  "...": "..."
}
```

### Get one — `GET /api/tickets/{id}` → `200` / `404`

```bash
curl http://localhost:8080/api/tickets/1     -H "X-API-Key: local-dev-key"
curl -i http://localhost:8080/api/tickets/9999 -H "X-API-Key: local-dev-key"   # 404 ApiError
```

### List (paginated, optional filter) — `GET /api/tickets?severity=&page=&size=&sort=`

```bash
curl "http://localhost:8080/api/tickets?severity=CRITICAL&page=0&size=20" -H "X-API-Key: local-dev-key"
```

```json
{ "content": [ /* ... */ ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true }
```

### Error responses

- Missing/invalid key → `401`
- Validation failure → `400` with a `fieldErrors` map
- Unknown id → `404`

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/tickets", "fieldErrors": { "subject": "subject must not be blank" } }
```

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `app.security.api-key` | `${API_KEY:local-dev-key}` | Required key for `/api/**` |
| `app.security.header-name` | `X-API-Key` | Header carrying the key |
| `triage.mode` | `stub` | `stub` (offline) or `llm` |
| `triage.api-url` | `https://api.anthropic.com` | Messages API base URL |
| `triage.api-key` | `${ANTHROPIC_API_KEY:}` | Sent as `x-api-key` |
| `triage.model` | `claude-haiku-4-5-20251001` | Model id |
| `triage.max-tokens` | `256` | Generation cap |
| `triage.timeout-seconds` | `20` | Per-request timeout |
| `triage.max-retries` | `2` | Retries on transient 5xx |
| `async.triage.*` | core 2 / max 8 / queue 500 | Triage executor sizing |

## Test

```bash
mvn test       # unit + slice + integration
mvn verify     # same, plus packaging
```

Coverage by layer:

| Test | Type | What it proves |
|---|---|---|
| `StubTriageClientTest` | unit (parameterized) | Core classification + queue routing |
| `LlmTriageClientTest` | unit (`MockWebServer`) | Response parsing, retry on 5xx, fallback, no-retry on 4xx |
| `TicketServiceTest` | unit (Mockito) | Persists `PENDING`, publishes event; reads; pagination |
| `TriageEventListenerTest` | unit (Mockito) | Applies verdict / marks failed; emits metric |
| `TicketControllerTest` | `@WebMvcTest` slice | 202 + `Location`, 400 validation, 404, pagination envelope |
| `ApiIntegrationTest` | `@SpringBootTest` | API-key enforcement, async 202→COMPLETED, end-to-end routing |

## Design decisions & trade-offs

- **Async over synchronous triage.** Ingest is decoupled from classification so a slow or failing model never blocks (or fails) ticket intake. The trade-off is eventual consistency: a freshly created ticket is briefly `PENDING`. At higher scale this in-process executor would be replaced by a durable queue (SQS/Kafka) — the event boundary is already in place for that.
- **API key, not OAuth.** A shared key is appropriate for a service-to-service internal tool and keeps the demo runnable. A real deployment would move to OIDC/JWT and per-caller identities; the `SecurityFilterChain` is the single seam to change.
- **H2 by default, Postgres by profile.** Keeps `mvn test` and local runs zero-dependency while proving the migration runs on a real RDBMS. The Flyway DDL is written to be valid on both.
- **Rule-based default classifier.** Deterministic, offline, and testable; the LLM path is opt-in so the service has no hard dependency on an external API.

## Project layout

```
src/main/java/com/example/tickettriage
├── TicketTriageApplication.java
├── config/        AsyncConfig, OpenApiConfig
├── domain/        Ticket, Severity, TriageStatus
├── repository/    TicketRepository
├── security/      SecurityConfig, ApiKeyAuthFilter, ApiKeyAuthEntryPoint, AppSecurityProperties
├── service/       TicketService, TicketCreatedEvent, TriageEventListener
├── triage/        TriageClient, StubTriageClient, LlmTriageClient, TriageResult, TriageProperties
└── web/           TicketController, GlobalExceptionHandler, ApiError, TicketNotFoundException, dto/
```
