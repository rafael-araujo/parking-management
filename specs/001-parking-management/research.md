# Research: Parking Management System

**Feature**: 001-parking-management
**Date**: 2026-03-10
**Status**: Complete — all decisions resolved

---

## 1. Maven Dependencies (Spring Boot 3.x + Java 21)

**Decision**: Use the following starters and dependencies in `pom.xml`:

| Dependency                          | Purpose                                          |
|-------------------------------------|--------------------------------------------------|
| `spring-boot-starter-web`           | REST API + embedded Tomcat                       |
| `spring-boot-starter-data-jpa`      | Hibernate ORM + Spring Data repositories         |
| `com.mysql:mysql-connector-j`       | MySQL 8 JDBC driver                              |
| `org.flywaydb:flyway-core`          | Schema migrations (auto-configured by Spring Boot)|
| `org.flywaydb:flyway-mysql`         | Flyway MySQL dialect support                     |
| `spring-boot-starter-validation`    | Bean validation (Jakarta Validation API)         |
| `spring-boot-starter-test`          | JUnit 5, Mockito, AssertJ                        |
| `org.testcontainers:testcontainers` | Containerized integration tests                  |
| `org.testcontainers:mysql`          | MySQL Testcontainer for integration tests        |
| `org.testcontainers:junit-jupiter`  | Testcontainers + JUnit 5 integration             |

**Rationale**: Standard Spring Boot 3 composition. No extra frameworks needed. RestTemplate
is bundled with `spring-boot-starter-web` — no separate dependency required.

---

## 2. Garage Initialization on Startup

**Decision**: Implement `ApplicationRunner` as a `@Component` bean.

- `ApplicationRunner.run()` executes after the application context is fully loaded but
  before the app starts accepting external traffic (in single-node deployments).
- The runner calls `GET /garage` via `RestTemplate`, parses the response, and upserts
  all sectors and spots via JPA repositories.
- If the call fails (exception or non-200), throw a `RuntimeException` from `run()`.
  Spring Boot will exit with non-zero status — no silent degradation.

**Alternatives considered**:
- `@EventListener(ApplicationReadyEvent)` — fires after ready, technically after Tomcat
  is accepting requests; slightly less safe than `ApplicationRunner`.
- `@PostConstruct` on a `@Service` — less explicit; harder to test in isolation.

---

## 3. HTTP Client for Simulator Calls

**Decision**: `RestTemplate` (synchronous, blocking).

- The simulator call is a one-time startup operation; async/reactive overhead unnecessary.
- `RestTemplate` is simpler, familiar, and already available in `spring-boot-starter-web`.
- Spring Boot 3 marks `RestTemplate` as "in maintenance mode" in favor of `WebClient`,
  but it remains fully supported and appropriate for non-reactive apps.
- `RestTemplate` bean should be declared as a `@Bean` in a configuration class for
  testability (can be mocked or replaced in tests).

**Alternatives considered**:
- `WebClient` — better for async/streaming; adds complexity for a blocking startup call.
- `HttpClient` (Java 11+) — lower level; more boilerplate; no benefit here.

---

## 4. Idempotency for Webhook Events

**Decision**: Application-level status check + DB unique constraint as safety net.

**Primary guard** (application layer):
- Before creating a new `VehicleSession`, query for an existing session with the same
  `license_plate` and `status IN ('ENTERING', 'PARKED')`.
- If found, return HTTP 200 immediately (no duplicate created).

**Secondary guard** (database layer):
- Unique index on `(license_plate, entry_time)` in `vehicle_session` table.
- Acts as safety net against race conditions or bugs in the application layer.

**Rationale**: Two-layer defense is clean and sufficient for a single-node system.
A separate idempotency-key table would add complexity without benefit here since the
simulator events carry natural unique keys (`license_plate` + `entry_time`).

---

## 5. Concurrent Occupancy Counting

**Decision**: Pessimistic locking (`SELECT FOR UPDATE`) on the `ParkingSpot` record being
reserved.

- When processing an ENTRY event, lock all available spots for the target sector with
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` before selecting and occupying one.
- This prevents two concurrent ENTRY events from occupying the same spot.
- Lock scope is minimal (only the specific spot being reserved), so contention is low.

**Alternatives considered**:
- **Optimistic locking** (`@Version`): Causes retry cycles under concurrent ENTRY events;
  more complex error handling required.
- **DB unique constraint on occupied=true**: Not directly expressible; would require
  a separate "occupation" record table.
- **In-memory lock (synchronized block)**: Not safe for future multi-instance deployments.

---

## 6. Currency Precision

**Decision**: `BigDecimal` for all monetary values. `DECIMAL(10, 2)` in the database.

- `double` / `float` have binary floating-point rounding errors; unacceptable for billing.
- `BigDecimal` with `RoundingMode.HALF_UP` ensures predictable rounding.
- Ceiling for billing hours uses `Math.ceil()` on duration before converting to `BigDecimal`.
- JPA mapping: `@Column(precision = 10, scale = 2)`.

---

## 7. Flyway Migration Strategy

**Decision**: Auto-configured Flyway with SQL migrations in `classpath:db/migration`.

- Migration files: `V1__create_garage_sector.sql`, `V2__create_parking_spot.sql`,
  `V3__create_vehicle_session.sql`.
- Naming convention: `V{version}__{description}.sql` (two underscores).
- `spring.flyway.baseline-on-migrate=false` (clean database; no pre-existing schema).
- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate validates against Flyway-managed schema.
- Flyway executes before Hibernate validation, ensuring schema is always up to date at startup.

---

## 8. Key Design Decision: Occupancy Scope for Dynamic Pricing

**Decision**: Occupancy ratio for dynamic pricing is computed across the **entire garage**
(sum of occupied spots / sum of max_capacity across all sectors).

**Rationale**: The test spec states the garage has "um único grupo de cancelas" (one
entrance group). There is no way to know at ENTRY time which sector the vehicle will park
in — that is only resolved by the PARKED event. Therefore, occupancy must be computed at
the garage level, not the sector level, to apply pricing at ENTRY.

Revenue per sector is determined by the vehicle's `spot.sector` after the PARKED event.

**Alternative considered**: Per-sector pricing at ENTRY (requires sector assignment before
PARKED, which is not possible without arbitrary sector selection). Rejected as inconsistent
with the single-entrance model described in the test spec.

---

## Summary

| Decision                  | Choice                                    |
|---------------------------|-------------------------------------------|
| HTTP client               | RestTemplate                              |
| Startup hook              | ApplicationRunner                         |
| Idempotency               | Status check + unique DB constraint       |
| Occupancy locking         | Pessimistic lock (PESSIMISTIC_WRITE)      |
| Currency type             | BigDecimal, DECIMAL(10,2)                 |
| Schema migrations         | Flyway (SQL, auto-configured)             |
| Integration test database | Testcontainers (MySQL)                    |
| Occupancy scope           | Whole garage (all sectors combined)       |
