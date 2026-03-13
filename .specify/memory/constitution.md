<!--
  SYNC IMPACT REPORT
  ==================
  Version change: 1.3.0 → 1.4.0
  Modified principles:
    - 1.3.0 (MINOR): Fixed outdated references in Principles I and II
      (domain/rules → domain/service; BillingRules/PricingRules → BillingService/PricingService;
      GlobalExceptionHandler moved to application/exception/).
      Added Principle VIII (Naming Conventions) covering class suffixes, variable
      naming in singular form, and collection variable suffixes (List, Set, Map).
      Added Principle IX (@Transactional requirement for all write methods in
      @Service and @Component classes).
    - 1.4.0 (MINOR): Added Principle X (API Documentation via OpenAPI/Swagger) mandating
      SpringDoc OpenAPI dependency, OpenApiConfig bean, @Tag/@Operation/@ApiResponse on
      controllers, @Schema on request/response classes, and Swagger UI at /swagger-ui.html.
  Added sections:
    - Principle VIII: Naming Conventions
    - Principle IX: Transactional Integrity for Write Operations
    - Principle X: API Documentation via OpenAPI/Swagger
  Removed sections: N/A
  Templates requiring updates:
    - .specify/templates/plan-template.md ✅ aligned (no changes required)
    - .specify/templates/spec-template.md ✅ aligned (no changes required)
    - .specify/templates/tasks-template.md ✅ aligned (no changes required)
    - 1.5.0 (MINOR): Documented missing implemented elements:
      Principle I: added domain/exception/ and domain/model/enums/ sub-packages.
      Principle VI: added exception class ↔ HTTP status mapping table and layer rules.
      Principle VIII: added Exception and Enum suffix rows; added enum naming rule section.
      Technology Constraints: added RestTemplate timeout requirements (connect 5s, read 10s).
  Deferred TODOs: none
-->

# Parking Management Constitution

## Core Principles

### I. Clean Architecture (NON-NEGOTIABLE)

The system MUST follow Clean Architecture with four explicit package layers:

- **`domain`**: Pure business objects and rules. No framework dependencies.
  - `domain/model/` — Domain DTOs (inter-layer data carriers) and domain enums with `DTO` suffix (e.g., `SessionStatusDTO`, `ControlTypeDTO`, `GateTypeDTO`).
  - `domain/model/enums/` — Domain enums with `Enum` suffix whose values map directly to event/protocol types (e.g., `EventTypeEnum`).
  - `domain/service/` — Pure business logic (e.g., `BillingService`, `PricingService`).
  - `domain/exception/` — Domain-level unchecked exceptions (e.g., `BusinessException`).
- **`application`**: Use-case orchestration. Depends only on `domain`.
  - `application/usecase/` — Orchestrators (e.g., `WebhookUseCase`, `RevenueUseCase`, `SectorUseCase`).
  - `application/mapper/` — Converts between requests/responses, DTOs, and entities.
  - `application/exception/` — `GlobalExceptionHandler` (`@RestControllerAdvice`) and typed application exception classes (e.g., `ResourceNotFoundException`, `ConflictException`, `InvalidEventException`, `ExternalServiceException`).
- **`adapter`**: HTTP interface. Depends on `application` and `domain`.
  - `adapter/controller/` — REST controllers only (no exception handling).
  - `adapter/model/request/` — HTTP request objects (only used in the controller layer).
  - `adapter/model/response/` — HTTP response objects (only used in the controller layer).
- **`infrastructure`**: Frameworks and external integrations. Depends on `domain`.
  - `infrastructure/persistence/entity/` — JPA `@Entity` classes (only used in repository and mapper layers).
  - `infrastructure/persistence/repository/` — Spring Data JPA repositories.
  - `infrastructure/client/` — External API clients (e.g., simulator).
  - `infrastructure/config/` — Spring configuration beans.

**Object boundaries** (NON-NEGOTIABLE):
- `request`/`response` objects are ONLY used in `adapter/controller/`.
- `entity` objects are ONLY used in `infrastructure/persistence/` and `application/mapper/`.
- `dto` (domain model) objects are used for communication between `application` and `adapter`.
- Mapper converts: `request → dto`, `dto → entity`, `entity → dto`, `dto → response`.

**Dependency rule**: `adapter` → `application` → `domain` ← `infrastructure`
- No inner layer may import from an outer layer.
- Cross-layer dependencies are strictly inward; circular dependencies are forbidden.

Package root: `com.parking.management.{adapter|application|domain|infrastructure}`

**Rationale**: Clean Architecture ensures testability, clear boundaries, and enforces the
principle that business rules are independent of delivery mechanisms and data sources.

### II. Business Rules Isolation (NON-NEGOTIABLE)

All domain rules MUST reside in dedicated classes under `domain/service/`:
- Dynamic pricing (occupancy tiers: <25%, <50%, <75%, ≤100%) in `PricingService`.
- Duration billing (free ≤30 min, per-hour ceiling otherwise) in `BillingService`.
- Sector capacity enforcement (block ENTRY when 100% occupied) in `SectorUseCase`.

No pricing or occupancy logic may be inlined in controllers, event handlers, or repositories.

**Rationale**: Isolating rules makes them independently testable and easy to audit.

### III. Idempotent Event Processing

Webhook events (ENTRY, PARKED, EXIT) MUST be processed idempotently. Re-delivery of the
same event for the same `license_plate` + timestamp MUST NOT create duplicate records or
double-charge. Each vehicle session MUST be tracked by a unique session entity keyed on
`license_plate` + `entry_time`.

**Rationale**: The simulator may retry events; double-billing or ghost occupancy would
silently corrupt revenue and capacity data.

### IV. Data Integrity Over Availability

Spot occupancy and revenue totals MUST be transactionally consistent. Operations that mutate
occupancy (ENTRY, EXIT) MUST use database transactions. Revenue records MUST be persisted
atomically with the spot release on EXIT. The system MUST return HTTP 409 (Conflict) for
ENTRY events when the sector is at 100% capacity rather than silently dropping or queuing.

**Rationale**: Accuracy of occupancy and billing is the primary correctness requirement of
this system; availability is secondary.

### V. Test Coverage for Business Rules

Unit tests MUST cover:
- All four dynamic pricing tiers.
- Billing for exactly 30 min (free boundary), 31 min (first chargeable hour), and multi-hour stays.
- Sector capacity enforcement (block at 100%, allow after EXIT).

Integration tests SHOULD cover the full ENTRY → PARKED → EXIT webhook cycle and the
`GET /revenue` endpoint with real data.

**Rationale**: Business rule correctness is explicitly listed as an evaluation criterion.

### VI. HTTP Error Handling (NON-NEGOTIABLE)

All HTTP errors MUST be handled by `GlobalExceptionHandler` in `application/exception/`. The
following status codes and their semantics MUST be respected:

| Code | Name | When to use | Exception class |
|------|------|-------------|-----------------|
| 400 | Bad Request | Malformed JSON, missing required fields, invalid format, unrecognised event type | `InvalidEventException` (application), `HttpMessageNotReadableException` (Spring), `MethodArgumentNotValidException` (Spring) |
| 404 | Not Found | Requested resource ID does not exist | `ResourceNotFoundException` (application) |
| 405 | Method Not Allowed | Endpoint exists but does not support the HTTP method | `HttpRequestMethodNotSupportedException` (Spring) |
| 409 | Conflict | State conflict (e.g., duplicate resource, sector at full capacity) | `ConflictException` (application) |
| 422 | Unprocessable Entity | Business rule violation (e.g., invalid state transition) | `BusinessException` (domain) |
| 500 | Internal Server Error | Last-resort catch-all for unhandled exceptions; MUST NOT expose stack traces | `Exception` (catch-all) |
| 503 | Service Unavailable | External service (simulator) failed or is unreachable | `ExternalServiceException` (application) |
| 504 | Gateway Timeout | Upstream service (simulator, DB) did not respond in time | — (reserved for future use) |

Exception classes MUST reside in their respective layers:
- `domain/exception/` — `BusinessException` (domain rule violations, maps to 422).
- `application/exception/` — `ResourceNotFoundException` (404), `ConflictException` (409), `InvalidEventException` (400), `ExternalServiceException` (503).

No exception class may be thrown across layers in the wrong direction (e.g., `application` exceptions MUST NOT be thrown from `domain`).

No controller or use case may return raw exception messages or stack traces to the client.
All error responses MUST go through `GlobalExceptionHandler`.

**Rationale**: Consistent error handling improves client integration and hides internal details.

### VII. Standard Error Response Format (NON-NEGOTIABLE)

Every error response MUST use the following JSON structure, represented by `ErrorResponse`:

```json
{
  "timestamp": "2024-03-11T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados de entrada inválidos",
  "path": "/api/endpoint",
  "details": [
    { "field": "email", "message": "O e-mail informado já está em uso" }
  ]
}
```

- `timestamp`: ISO-8601 UTC instant of the error.
- `status`: HTTP status code integer.
- `error`: HTTP reason phrase.
- `message`: Human-readable summary of the error.
- `path`: Request URI that triggered the error.
- `details`: Optional list of field-level errors (used for validation errors; empty otherwise).

`ErrorResponse` and its inner `FieldDetail` class MUST reside in `adapter/model/response/`.

**Rationale**: Consistent error format makes API integration predictable for all consumers.

### VIII. Naming Conventions (NON-NEGOTIABLE)

#### Class suffixes

Every class MUST be named with a suffix that reflects its role in the architecture:

| Suffix | Layer | Example |
|--------|-------|---------|
| `Entity` | `infrastructure/persistence/entity/` | `VehicleSessionEntity` |
| `DTO` | `domain/model/` | `RevenueDTO`, `SessionStatusDTO` |
| `Service` | `domain/service/` | `BillingService`, `PricingService` |
| `UseCase` | `application/usecase/` | `WebhookUseCase`, `SectorUseCase` |
| `Mapper` | `application/mapper/` | `RevenueMapper` |
| `Repository` | `infrastructure/persistence/repository/` | `VehicleSessionRepository` |
| `Controller` | `adapter/controller/` | `WebhookController` |
| `Request` | `adapter/model/request/` | `WebhookEventRequest` |
| `Response` | `adapter/model/response/` | `RevenueResponse`, `ErrorResponse` |
| `Client` | `infrastructure/client/` | `SimulatorClient` |
| `Exception` | `domain/exception/` or `application/exception/` | `BusinessException`, `ConflictException` |
| `DTO` (enum) | `domain/model/` | `SessionStatusDTO`, `ControlTypeDTO`, `GateTypeDTO` |
| `Enum` | `domain/model/enums/` | `EventTypeEnum` |

Classes MUST NOT use suffixes from a different layer (e.g., an entity class MUST NOT be named `VehicleSessionDTO`).

#### Enum naming rule

Domain enums follow two conventions depending on their purpose:
- Enums that represent the **state or type of a domain object** (e.g., session status, gate type, control type) MUST use the `DTO` suffix and reside in `domain/model/`.
- Enums that represent **event/protocol values** that arrive from external systems (e.g., webhook event type) MUST use the `Enum` suffix and reside in `domain/model/enums/`.

#### Variable and field naming

- Variable and field names MUST be in singular form and describe the content or type of the object.
- Collection variables MUST use the following suffixes to reflect their concrete type:

| Type | Required suffix | Example |
|------|-----------------|---------|
| `List<T>` | `List` | `sectorList`, `gateList` |
| `Set<T>` | `Set` | `licensePlateSet` |
| `Map<K,V>` | `Map` | `sectorByIdMap` |
| `Queue<T>` | `Queue` | `eventQueue` |
| `Deque<T>` | `Deque` | `entryDeque` |
| Other collection | descriptive suffix | reflect the concrete type |

**Rationale**: Consistent suffixes make the type and purpose of every identifier immediately clear at the call site, reducing cognitive load during code review.

### IX. Transactional Integrity for Write Operations (NON-NEGOTIABLE)

Every method in a `@Service` or `@Component` class that performs any database mutation
(i.e., calls `save`, `saveAll`, `delete`, `deleteAll`, `deleteById`, or any modifying JPQL/native query)
MUST be annotated with `@Transactional`.

- Read-only methods MAY use `@Transactional(readOnly = true)` when they span multiple repository calls that must observe a consistent snapshot.
- `@Transactional` on the class level is NOT permitted as a substitute for method-level annotation — each mutating method must be explicitly annotated so that the intent is visible at the call site.
- Methods that call other `@Transactional` methods within the same class do NOT bypass this rule; each entry point into a write operation must be independently annotated.

**Rationale**: Explicit transaction boundaries prevent partial writes, ensure atomicity of multi-step mutations, and make transaction scope visible without requiring full class inspection.

### X. API Documentation via OpenAPI/Swagger (NON-NEGOTIABLE)

Every REST endpoint MUST be documented using SpringDoc OpenAPI (`springdoc-openapi-starter-webmvc-ui`).

#### Requirements

- Dependency: `org.springdoc:springdoc-openapi-starter-webmvc-ui` MUST be present in `pom.xml`.
- A single `OpenApiConfig` class in `infrastructure/config/` MUST declare an `@Bean` of type `OpenAPI` with:
  - `title`, `description`, `version`, and `contact` metadata for the API.
- Every controller class MUST be annotated with `@Tag(name = "...", description = "...")`.
- Every controller method MUST be annotated with:
  - `@Operation(summary = "...", description = "...")` — short summary and detailed description.
  - `@ApiResponse` (one per distinct HTTP status the method can return), using `@ApiResponses` when multiple.
- Every request class MUST annotate each field with `@Schema(description = "...", example = "...")`.
- Every response class MUST annotate each field with `@Schema(description = "...", example = "...")`.
- Swagger UI MUST be accessible at `/swagger-ui.html` (default SpringDoc path).
- OpenAPI JSON spec MUST be accessible at `/v3/api-docs`.

#### What is NOT permitted

- Swagger annotations on domain DTOs, entities, or use-case classes — only adapter-layer classes (`request`, `response`, `controller`) may carry OpenAPI annotations.
- Disabling Swagger in production via profiles without an explicit governance decision recorded in this constitution.

**Rationale**: Machine-readable API documentation reduces integration friction, enables contract testing, and provides a live sandbox for QA and client teams.

## Technology Constraints

- **Language**: Java 21 (LTS). No preview features unless justified.
- **Framework**: Spring Boot (latest stable 3.x). Use Spring Data JPA for persistence.
- **Database**: MySQL 8+. Schema migrations MUST use Flyway or Liquibase.
- **Build tool**: Maven. Use `pom.xml` for all dependency management. Gradle is not permitted.
- **Simulator**: Runs as a Docker container (`cfontes0estapar/garage-sim:1.0.0`). On application
  startup, `GET /garage` from the simulator MUST be called and its data persisted. The application
  MUST NOT start in a degraded state if this call fails; it SHOULD fail fast with a clear error.
- **HTTP client (`RestTemplate`)**: MUST be configured with explicit timeouts — `connectTimeout = 5 000 ms`, `readTimeout = 10 000 ms`. Configured as a `@Bean` in `infrastructure/config/AppConfig`.
- **Webhook endpoint**: The application MUST expose `POST /webhook` on port 3003.

## REST API Standards

- All responses MUST use `application/json`.
- `GET /revenue` accepts `date` (YYYY-MM-DD) and `sector` in the request body. Response body
  MUST include `amount`, `currency` ("BRL"), and `timestamp` (ISO-8601).
- HTTP status codes MUST be semantically correct per Principle VI.
- All error responses MUST follow the format defined in Principle VII.

## Governance

This constitution supersedes all other development practices for this project. Any practice
that contradicts a principle stated here is invalid until the constitution is formally amended.

**Amendment procedure**:
1. Propose the change with a rationale and version bump type (MAJOR/MINOR/PATCH).
2. Update this file and run the consistency propagation checklist against all dependent templates.
3. Record the amendment in the Sync Impact Report comment at the top of this file.

**Versioning policy**:
- MAJOR: Removal or redefinition of a principle (backward-incompatible governance change).
- MINOR: New principle or section added; material expansion of existing guidance.
- PATCH: Clarification, wording fix, or non-semantic refinement.

**Compliance review**: Every PR/task review MUST verify that the implementation does not
violate Principles I–X. Violations must be resolved before merge, not deferred.

**Version**: 1.5.0 | **Ratified**: 2026-03-10 | **Last Amended**: 2026-03-13
