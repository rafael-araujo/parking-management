---

description: "Task list for Parking Management System"
---

# Tasks: Parking Management System

**Input**: Design documents from `/specs/001-parking-management/`
**Prerequisites**: plan.md ✅ | spec.md ✅ | data-model.md ✅ | contracts/ ✅ | research.md ✅

**Tests**: Included — spec requires "basic error handling and tests"; Constitution Principle V
mandates unit tests for all pricing tiers, billing boundaries, and capacity enforcement.

**Organization**: Tasks grouped by user story for independent implementation and delivery.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1–US5)
- Exact file paths included in every task description

## Path Conventions

- Source: `src/main/java/com/parking/management/`
- Resources: `src/main/resources/`
- Tests: `src/test/java/com/parking/management/`

---

## Phase 1: Setup (Project Initialization)

**Purpose**: Create the Maven project skeleton with all dependencies and base configuration.

- [x] T001 Initialize Maven Spring Boot 3.x project with `pom.xml` containing all dependencies
  from research.md: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `com.mysql:mysql-connector-j`, `flyway-core`, `flyway-mysql`,
  `spring-boot-starter-validation`, `spring-boot-starter-test`,
  `testcontainers`, `testcontainers:mysql`, `testcontainers:junit-jupiter`; set Java 21 compiler
  target; set `spring-boot-maven-plugin`; groupId `com.parking`, artifactId `parking-management`
- [x] T002 [P] Configure `src/main/resources/application.yml` with: datasource url/username/
  password for MySQL, `spring.jpa.hibernate.ddl-auto=validate`,
  `spring.flyway.enabled=true`, `server.port=3003`,
  `simulator.base-url=http://localhost:3000`
- [x] T003 [P] Create Java package skeleton in `src/main/java/com/parking/management/`:
  `config/`, `controller/`, `dto/`, `model/`, `repository/`, `service/`; create
  `ParkingManagementApplication.java` with `@SpringBootApplication`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, JPA entities, repositories, and shared infrastructure that
ALL user stories depend on. No story work can begin until this phase is complete.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 Create Flyway migration `src/main/resources/db/migration/V1__create_garage_sector.sql`:
  table `garage_sector` with columns `sector VARCHAR(10) PRIMARY KEY`, `base_price DECIMAL(10,2) NOT NULL`,
  `max_capacity INT NOT NULL`
- [x] T005 [P] Create Flyway migration `src/main/resources/db/migration/V2__create_parking_spot.sql`:
  table `parking_spot` with columns `id BIGINT PRIMARY KEY`, `sector VARCHAR(10) NOT NULL`,
  `lat DECIMAL(10,8) NOT NULL`, `lng DECIMAL(11,8) NOT NULL`, `occupied BOOLEAN NOT NULL DEFAULT FALSE`;
  FK constraint `sector → garage_sector(sector)`
- [x] T006 [P] Create Flyway migration `src/main/resources/db/migration/V3__create_vehicle_session.sql`:
  table `vehicle_session` with columns `id BIGINT AUTO_INCREMENT PRIMARY KEY`,
  `license_plate VARCHAR(20) NOT NULL`, `entry_time DATETIME NOT NULL`,
  `exit_time DATETIME`, `spot_id BIGINT`, `sector VARCHAR(10) NOT NULL`,
  `price_multiplier DECIMAL(4,2) NOT NULL`, `amount_charged DECIMAL(10,2)`,
  `status ENUM('ENTERING','PARKED','EXITED') NOT NULL`;
  FKs: `spot_id → parking_spot(id)`, `sector → garage_sector(sector)`;
  UNIQUE index on `(license_plate, entry_time)`
- [x] T007 Create `src/main/java/com/parking/management/model/GarageSector.java`:
  `@Entity`, `@Table(name="garage_sector")`; fields: `@Id String sector`,
  `@Column(precision=10,scale=2) BigDecimal basePrice`, `int maxCapacity`
- [x] T008 [P] Create `src/main/java/com/parking/management/model/ParkingSpot.java`:
  `@Entity`, `@Table(name="parking_spot")`; fields: `@Id Long id`,
  `@ManyToOne GarageSector sector`, `BigDecimal lat`, `BigDecimal lng`,
  `boolean occupied`
- [x] T009 Create `src/main/java/com/parking/management/model/VehicleSession.java`
  and nested `SessionStatus` enum (`ENTERING`, `PARKED`, `EXITED`):
  `@Entity`, `@Table(name="vehicle_session")`; fields: `@Id @GeneratedValue Long id`,
  `String licensePlate`, `LocalDateTime entryTime`, `LocalDateTime exitTime`,
  `@ManyToOne ParkingSpot spot`, `@ManyToOne GarageSector sector`,
  `@Column(precision=4,scale=2) BigDecimal priceMultiplier`,
  `@Column(precision=10,scale=2) BigDecimal amountCharged`,
  `@Enumerated(EnumType.STRING) SessionStatus status`
- [x] T010 Create `src/main/java/com/parking/management/repository/GarageSectorRepository.java`:
  `JpaRepository<GarageSector, String>`; add `countAll()` and `sumMaxCapacity()` queries
- [x] T011 [P] Create `src/main/java/com/parking/management/repository/ParkingSpotRepository.java`:
  `JpaRepository<ParkingSpot, Long>`; add `countByOccupied(boolean occupied)`,
  `countByOccupiedTrue()`, and `findFirstByOccupiedFalse()` with
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- [x] T012 [P] Create `src/main/java/com/parking/management/repository/VehicleSessionRepository.java`:
  `JpaRepository<VehicleSession, Long>`; add
  `findByLicensePlateAndStatusIn(String plate, List<SessionStatus> statuses)` and
  `findByLicensePlateAndStatus(String plate, SessionStatus status)`
- [x] T013 Create `src/main/java/com/parking/management/config/AppConfig.java`:
  `@Configuration`; declare `@Bean RestTemplate restTemplate()`
- [x] T014 [P] Create DTOs in `src/main/java/com/parking/management/dto/`:
  `WebhookEventDto` (licensePlate, entryTime, exitTime, lat, lng, eventType as String),
  `RevenueRequestDto` (date as LocalDate, sector as String with `@NotNull @NotBlank`),
  `RevenueResponseDto` (amount as BigDecimal, currency hardcoded "BRL", timestamp as Instant)
- [x] T015 Create `src/main/java/com/parking/management/controller/GlobalExceptionHandler.java`:
  `@ControllerAdvice`; handle `MethodArgumentNotValidException` → 400 with message,
  `ResponseStatusException` → forward status+message, generic `Exception` → 500

**Checkpoint**: Schema migrated, entities mapped, repositories ready — user story work can begin.

---

## Phase 3: User Story 1 - Garage Initialization on Startup (Priority: P1) 🎯 MVP

**Goal**: On startup, fetch garage config from simulator and persist all sectors and spots.

**Independent Test**: Start the app with simulator running. Query `garage_sector` and
`parking_spot` tables directly — both must contain rows matching GET /garage response.

### Implementation for User Story 1

- [x] T016 [US1] Create `src/main/java/com/parking/management/dto/SimulatorGarageResponseDto.java`
  with nested records `GarageConfigDto(sector, basePrice, max_capacity)` and
  `SpotConfigDto(id, sector, lat, lng)` and outer fields `List<GarageConfigDto> garage`,
  `List<SpotConfigDto> spots`
- [x] T017 [US1] Implement `src/main/java/com/parking/management/service/GarageInitializationService.java`
  as `@Service` implementing `ApplicationRunner`; inject `RestTemplate`, `GarageSectorRepository`,
  `ParkingSpotRepository`, and `@Value("${simulator.base-url}")` String; in `run()`: call
  `GET {baseUrl}/garage`, deserialize to `SimulatorGarageResponseDto`, upsert each sector via
  `saveAll()`, upsert each spot via `saveAll()` (set `occupied=false` only if new);
  throw `RuntimeException` if HTTP call fails

### Tests for User Story 1 ⚠️

- [x] T018 [P] [US1] Unit test `src/test/java/com/parking/management/service/GarageInitializationServiceTest.java`:
  mock `RestTemplate` to return sample response; verify `saveAll()` called with correct sector
  and spot data; verify exception thrown when RestTemplate throws `RestClientException`

**Checkpoint**: Application starts, populates DB from simulator, fails fast on error.

---

## Phase 4: User Story 2 - Vehicle Entry and Spot Assignment (Priority: P1) 🎯 MVP

**Goal**: ENTRY webhook marks a spot as occupied, creates a session with the correct
dynamic pricing multiplier, and rejects entry when garage is full (HTTP 409).

**Independent Test**: POST ENTRY event → verify new `vehicle_session` row with correct
`price_multiplier`, one `parking_spot.occupied = true`; POST ENTRY when all spots occupied
→ verify HTTP 409.

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T019 [P] [US2] Unit test `src/test/java/com/parking/management/service/PricingServiceTest.java`:
  test `getMultiplier()` with ratios: 0.00 (→ 0.90), 0.24 (→ 0.90), 0.25 (→ 1.00),
  0.49 (→ 1.00), 0.50 (→ 1.10), 0.74 (→ 1.10), 0.75 (→ 1.25), 0.99 (→ 1.25); use
  `assertEquals` with `BigDecimal` comparisons (`compareTo` not `equals`)
- [x] T020 [P] [US2] Unit test `src/test/java/com/parking/management/service/SectorServiceTest.java`:
  test `isGarageFull()` returns true when occupiedCount == totalCapacity; returns false otherwise;
  test `reserveAvailableSpot()` marks selected spot as occupied and returns it

### Implementation for User Story 2

- [x] T021 [P] [US2] Implement `src/main/java/com/parking/management/service/PricingService.java`:
  `@Service`; method `BigDecimal getMultiplier(BigDecimal occupancyRatio)` applying the four
  tier rules: < 0.25 → 0.90, < 0.50 → 1.00, < 0.75 → 1.10, else → 1.25; use
  `BigDecimal.valueOf` for all constants
- [x] T022 [P] [US2] Implement `src/main/java/com/parking/management/service/SectorService.java`:
  `@Service`; inject `ParkingSpotRepository`, `GarageSectorRepository`;
  `boolean isGarageFull()`: count occupied spots vs. sum of all `max_capacity`;
  `BigDecimal getOccupancyRatio()`: `occupiedCount / totalCapacity`;
  `@Transactional ParkingSpot reserveAvailableSpot()`: call
  `findFirstByOccupiedFalse()` (pessimistic lock), set `occupied=true`, save and return
- [x] T023 [US2] Implement `WebhookService.processEntry(String licensePlate, LocalDateTime entryTime)`
  in `src/main/java/com/parking/management/service/WebhookService.java`: check idempotency
  (existing ENTERING/PARKED session for plate → return without action); call `sectorService
  .isGarageFull()` → throw `ResponseStatusException(HttpStatus.CONFLICT)` if true; get
  occupancy ratio; compute multiplier via `PricingService`; reserve spot via `SectorService`;
  create and save `VehicleSession(licensePlate, entryTime, spot, spot.sector, multiplier,
  status=ENTERING)`; annotate `@Transactional`
- [x] T024 [US2] Implement `src/main/java/com/parking/management/controller/WebhookController.java`:
  `@RestController @RequestMapping("/webhook")`; `@PostMapping` method receiving
  `@Valid @RequestBody WebhookEventDto`; route `eventType` to service: `ENTRY →
  webhookService.processEntry()`; return `ResponseEntity.ok().build()` on success;
  let `GlobalExceptionHandler` handle errors

**Checkpoint**: ENTRY webhook works end-to-end; HTTP 409 on full garage; pricing tiers verified.

---

## Phase 5: User Story 4 - Vehicle Exit and Billing (Priority: P1) 🎯 MVP

**Goal**: EXIT webhook closes the session, computes the fee, releases the spot, and persists
the billing record. Free for ≤30 min; hourly ceiling otherwise.

**Independent Test**: POST ENTRY then EXIT 20 min later → verify `amount_charged = 0.00`,
spot released; POST ENTRY then EXIT 31 min later → verify `amount_charged = basePrice × multiplier × 1`.

### Tests for User Story 4 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T025 [P] [US4] Unit test `src/test/java/com/parking/management/service/BillingServiceTest.java`:
  test `calculate()` with: 20 min (→ 0.00), 30 min exactly (→ 0.00), 31 min (→ basePrice × mult × 1),
  60 min exactly (→ basePrice × mult × 1), 61 min (→ basePrice × mult × 2), 90 min (→ basePrice × mult × 2);
  test each of the 4 multipliers (0.90, 1.00, 1.10, 1.25) with same duration; use `BigDecimal`

### Implementation for User Story 4

- [x] T026 [US4] Implement `src/main/java/com/parking/management/service/BillingService.java`:
  `@Service`; method `BigDecimal calculate(LocalDateTime entryTime, LocalDateTime exitTime,
  BigDecimal basePrice, BigDecimal multiplier)`: compute `durationMinutes` via `ChronoUnit
  .MINUTES.between(entryTime, exitTime)`; if `≤ 30` return `BigDecimal.ZERO`; else compute
  `billableHours = Math.ceil(durationMinutes / 60.0)` as long; return `basePrice.multiply
  (multiplier).multiply(BigDecimal.valueOf(billableHours)).setScale(2, RoundingMode.HALF_UP)`
- [x] T027 [US4] Implement `WebhookService.processExit(String licensePlate, LocalDateTime exitTime)`
  in `src/main/java/com/parking/management/service/WebhookService.java`: find active session
  (ENTERING or PARKED) for plate — if none, log warning and return; load `sector.basePrice`;
  call `billingService.calculate()`; update session: `exitTime`, `amountCharged`, `status=EXITED`;
  set `session.spot.occupied = false`; save session; annotate `@Transactional`
- [x] T028 [US4] Add EXIT routing in `WebhookController.handleWebhook()`:
  `EXIT → webhookService.processExit(dto.getLicensePlate(), dto.getExitTime())`
  in `src/main/java/com/parking/management/controller/WebhookController.java`

**Checkpoint**: Full ENTRY → EXIT cycle works; billing formula verified; spot correctly released.

---

## Phase 6: User Story 3 - PARKED Event Spot Association (Priority: P2)

**Goal**: PARKED event updates the session's spot to the GPS-matched physical spot.

**Independent Test**: POST ENTRY, then POST PARKED with lat/lng of a known spot → verify
`vehicle_session.spot_id` updated to the nearest spot's id.

### Implementation for User Story 3

- [x] T029 [US3] Add `findNearestToCoordinates` native SQL query to
  `src/main/java/com/parking/management/repository/ParkingSpotRepository.java`:
  `@Query` with `ORDER BY (POW(lat - :lat, 2) + POW(lng - :lng, 2)) ASC LIMIT 1`
  returning `Optional<ParkingSpot>`
- [x] T030 [US3] Implement `WebhookService.processParked(String licensePlate, BigDecimal lat,
  BigDecimal lng)` in `src/main/java/com/parking/management/service/WebhookService.java`:
  find active session for plate — if none, log warning and return; find nearest spot via
  `ParkingSpotRepository.findNearestToCoordinates(lat, lng)`; update `session.spot = nearestSpot`,
  `session.sector = nearestSpot.sector`, `session.status = PARKED`; save session;
  annotate `@Transactional`
- [x] T031 [US3] Add PARKED routing in `WebhookController.handleWebhook()`:
  `PARKED → webhookService.processParked(dto.getLicensePlate(), dto.getLat(), dto.getLng())`
  in `src/main/java/com/parking/management/controller/WebhookController.java`

**Checkpoint**: PARKED event correctly links session to GPS-matched spot.

---

## Phase 7: User Story 5 - Revenue Query by Sector and Date (Priority: P2)

**Goal**: `GET /revenue` returns the summed `amount_charged` for all EXITED sessions in a
given sector on a given date.

**Independent Test**: Complete 3 sessions in sector "A" on 2025-01-01 with known fees
→ call `GET /revenue {date: "2025-01-01", sector: "A"}` → verify `amount` equals sum of fees.

### Implementation for User Story 5

- [x] T032 [US5] Add revenue aggregation query to
  `src/main/java/com/parking/management/repository/VehicleSessionRepository.java`:
  `@Query("SELECT COALESCE(SUM(v.amountCharged), 0) FROM VehicleSession v
  WHERE v.sector.sector = :sector AND DATE(v.exitTime) = :date AND v.status = 'EXITED'")`
  method `BigDecimal sumRevenueBySectorAndDate(@Param("sector") String sector,
  @Param("date") LocalDate date)`
- [x] T033 [US5] Implement `src/main/java/com/parking/management/service/RevenueService.java`:
  `@Service`; inject `VehicleSessionRepository`, `GarageSectorRepository`;
  method `RevenueResponseDto getRevenue(String sector, LocalDate date)`: verify sector exists
  (throw `ResponseStatusException(NOT_FOUND)` if not); sum via repository query; return
  `new RevenueResponseDto(amount, "BRL", Instant.now())`
- [x] T034 [US5] Implement `src/main/java/com/parking/management/controller/RevenueController.java`:
  `@RestController`; `@GetMapping("/revenue")` receiving `@Valid @RequestBody RevenueRequestDto`;
  delegate to `RevenueService.getRevenue()`; return `ResponseEntity.ok(dto)`

**Checkpoint**: GET /revenue returns accurate totals; 0.00 for empty date/sector; 404 for unknown sector.

---

## Phase 8: Integration Tests & Polish

**Purpose**: End-to-end validation and cross-cutting improvements across all stories.

- [x] T035 [P] Integration test `src/test/java/com/parking/management/integration/WebhookIntegrationTest.java`:
  use `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `@Testcontainers` with MySQL container;
  seed garage_sector and parking_spot; POST ENTRY → POST PARKED → POST EXIT; assert HTTP 200 for
  each; verify `vehicle_session` row: status=EXITED, amountCharged correct, spot.occupied=false;
  test duplicate ENTRY returns 200 without duplicate session; test ENTRY with full garage returns 409
- [x] T036 [P] Integration test `src/test/java/com/parking/management/integration/RevenueIntegrationTest.java`:
  use `@SpringBootTest` + `@Testcontainers` with MySQL; insert completed sessions directly via
  repository; call `GET /revenue`; assert `amount` matches expected sum; assert 0.00 for empty
  sector/date; assert 404 for unknown sector
- [x] T037 Validate against `specs/001-parking-management/quickstart.md` checklist:
  run `mvn test`, confirm all tests pass; start app with simulator running; verify every item
  in the quickstart validation checklist produces expected results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Foundational — no story dependencies
- **US2 (Phase 4)**: Depends on Foundational — no story dependencies
- **US4 (Phase 5)**: Depends on US2 (needs active sessions to exit)
- **US3 (Phase 6)**: Depends on US2 (needs active sessions to park)
- **US5 (Phase 7)**: Depends on US4 (needs EXITED sessions with amounts)
- **Polish (Phase 8)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Independent after Foundational
- **US2 (P1)**: Independent after Foundational; US4 must wait for US2
- **US4 (P1)**: Depends on US2
- **US3 (P2)**: Depends on US2; can start after US4 MVP checkpoint
- **US5 (P2)**: Depends on US4

### Within Each User Story

- Tests written FIRST (must fail before implementation)
- Models before services
- Services before controllers/endpoints
- Idempotency and error handling inline with service implementation
- Story complete before moving to next phase

### Parallel Opportunities

- T002, T003 can run in parallel after T001
- T005, T006 can run in parallel after T004
- T008 can run after T007; T009 can run after T007+T008
- T011, T012 can run in parallel after T010
- T019, T020 (tests) can run in parallel before US2 implementation
- T021, T022 (PricingService, SectorService) can run in parallel
- T035, T036 (integration tests) can run in parallel

---

## Parallel Examples

```bash
# Phase 2 migrations (after T004):
Task: T005 - Create V2 parking_spot migration
Task: T006 - Create V3 vehicle_session migration

# Phase 4 tests (write first, verify they fail):
Task: T019 - PricingServiceTest (all 4 tiers)
Task: T020 - SectorServiceTest (capacity enforcement)

# Phase 4 service implementations (after tests):
Task: T021 - PricingService implementation
Task: T022 - SectorService implementation

# Phase 8 integration tests (independent):
Task: T035 - WebhookIntegrationTest
Task: T036 - RevenueIntegrationTest
```

---

## Implementation Strategy

### MVP First (P1 User Stories Only: US1 + US2 + US4)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: US1 (startup initialization)
4. Complete Phase 4: US2 (vehicle entry + pricing)
5. Complete Phase 5: US4 (vehicle exit + billing)
6. **STOP and VALIDATE**: Full ENTRY → EXIT cycle works; billing correct; GET /revenue returns 0 (no US5 yet)
7. This is a deployable MVP demonstrating the core parking workflow.

### Incremental Delivery

1. Setup + Foundational → infrastructure ready
2. US1 → app initializes from simulator
3. US2 → vehicles can enter with correct pricing
4. US4 → vehicles can exit with correct billing ← **P1 MVP complete**
5. US3 → PARKED events correctly link GPS coordinates
6. US5 → revenue querying operational ← **P2 complete**
7. Integration tests + Polish

### Notes

- [P] tasks = different files, no dependencies within the same phase
- [Story] label maps task to specific user story for traceability
- Tests marked with `[P]` in the same phase can be written in parallel
- Verify tests FAIL before implementing the code they test
- Commit after each checkpoint (end of each phase)
- Stop at each checkpoint to validate the story independently before proceeding
