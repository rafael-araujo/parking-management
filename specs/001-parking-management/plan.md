# Implementation Plan: Parking Management System

**Branch**: `001-parking-management` | **Date**: 2026-03-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-parking-management/spec.md`

## Summary

Backend REST API that manages a simulated parking garage: persists garage configuration
on startup, processes vehicle lifecycle webhook events (ENTRY/PARKED/EXIT), enforces
sector capacity with dynamic pricing, and exposes a revenue query endpoint. Built with
Java 21, Spring Boot 3.x, JPA/Hibernate, MySQL 8, and Flyway migrations.

## Technical Context

**Language/Version**: Java 21 (LTS)
**Primary Dependencies**: Spring Boot 3.x, Spring Data JPA (Hibernate), Spring Web,
  RestTemplate, Flyway, spring-boot-starter-validation, Testcontainers (MySQL)
**Storage**: MySQL 8.0+ with Flyway schema migrations
**Testing**: JUnit 5 + Mockito (unit); Testcontainers + Spring Boot Test (integration)
**Target Platform**: Linux server, single-node JVM process
**Project Type**: web-service (REST API + webhook receiver)
**Performance Goals**: Standard web service; no explicit SLA defined in test spec
**Constraints**: Webhook endpoint on port 3003; simulator accessible at localhost
**Scale/Scope**: Single garage instance managed by the simulator; single node

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status | Notes |
|-----------|------|--------|-------|
| I. Layered Architecture | Controllers contain HTTP I/O only; no business logic | ✅ PASS | WebhookController + RevenueController delegate to services |
| II. Business Rules Isolation | PricingService, BillingService, SectorService defined | ✅ PASS | All domain logic in dedicated service classes |
| III. Idempotent Event Processing | Duplicate events must not create double records | ✅ PASS | Status check + unique DB constraint on (license_plate, entry_time) |
| IV. Data Integrity | Transactional occupancy mutations; 409 on full garage | ✅ PASS | Pessimistic lock on spot reservation; @Transactional on EXIT |
| V. Test Coverage | Unit tests for all 4 tiers, billing boundaries, capacity enforcement | ✅ PASS | Covered in PricingServiceTest, BillingServiceTest, SectorServiceTest |
| Tech: Maven | pom.xml for all dependencies | ✅ PASS | Maven project structure planned |
| Tech: Java 21 | No preview features | ✅ PASS | Standard Java 21 only |
| Tech: MySQL + Flyway | Schema migrations via Flyway | ✅ PASS | V1/V2/V3 migration files planned |

**All gates PASS. No violations.**

## Project Structure

### Documentation (this feature)

```text
specs/001-parking-management/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── webhook-api.md
│   ├── revenue-api.md
│   └── simulator-client.md
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── com/parking/management/
│   │       ├── ParkingManagementApplication.java
│   │       ├── config/
│   │       │   └── AppConfig.java              # RestTemplate bean
│   │       ├── controller/
│   │       │   ├── WebhookController.java       # POST /webhook
│   │       │   └── RevenueController.java       # GET /revenue
│   │       ├── dto/
│   │       │   ├── WebhookEventDto.java
│   │       │   ├── RevenueRequestDto.java
│   │       │   └── RevenueResponseDto.java
│   │       ├── model/
│   │       │   ├── GarageSector.java            # @Entity
│   │       │   ├── ParkingSpot.java             # @Entity
│   │       │   └── VehicleSession.java          # @Entity + SessionStatus enum
│   │       ├── repository/
│   │       │   ├── GarageSectorRepository.java
│   │       │   ├── ParkingSpotRepository.java
│   │       │   └── VehicleSessionRepository.java
│   │       └── service/
│   │           ├── GarageInitializationService.java  # ApplicationRunner
│   │           ├── PricingService.java               # Occupancy tier → multiplier
│   │           ├── BillingService.java               # Duration → amount
│   │           ├── SectorService.java                # Capacity check + spot reservation
│   │           └── WebhookService.java               # Orchestrates ENTRY/PARKED/EXIT
│   └── resources/
│       ├── application.yml
│       └── db/
│           └── migration/
│               ├── V1__create_garage_sector.sql
│               ├── V2__create_parking_spot.sql
│               └── V3__create_vehicle_session.sql
└── test/
    └── java/
        └── com/parking/management/
            ├── service/
            │   ├── PricingServiceTest.java      # Unit: all 4 occupancy tiers
            │   ├── BillingServiceTest.java      # Unit: free period, 1h, multi-hour
            │   └── SectorServiceTest.java       # Unit: capacity enforcement
            └── integration/
                ├── WebhookIntegrationTest.java  # ENTRY → PARKED → EXIT cycle
                └── RevenueIntegrationTest.java  # GET /revenue accuracy
```

**Structure Decision**: Single Spring Boot project. Backend-only, no frontend.
Maven standard layout (`src/main/java`, `src/test/java`).

## Key Design Decisions

### Dual Control Types: PHYSICAL vs LOGICAL

Each `GarageSector` has a `control_type`:

- **PHYSICAL**: Classic spot-based control. On ENTRY, a physical `ParkingSpot` is reserved
  (pessimistic write lock). On PARKED, the session is linked to the GPS-nearest spot.
  On EXIT, the spot is released. Sector is derived from the reserved spot.
- **LOGICAL**: Count-based control. On ENTRY, only a `VehicleSession` is created (no spot
  reserved). The sector is derived from the `ParkingGate` referenced by `gate_id` in the
  event. On PARKED, status is updated but no GPS → spot lookup occurs. On EXIT, only the
  session is closed (no spot to release).

### Cancelas (ParkingGate)

`ParkingGate` entities are fetched from the simulator at startup alongside sectors and spots.
Each gate has an `id`, a `sector` FK, and a `gate_type` (ENTRY/EXIT/BOTH). The `gate_id`
field in webhook events is used to look up the gate and resolve the sector for LOGICAL
control, and is recorded on the session for traceability in all cases.

### Occupancy Scope for Dynamic Pricing

Occupancy is now computed uniformly via **active sessions** (status ENTERING or PARKED)
divided by total max_capacity. This is consistent across both control types and avoids
maintaining a separate spot-count metric.

### Service Responsibilities

| Service | Responsibility |
|---------|---------------|
| `GarageInitializationService` | ApplicationRunner; calls simulator GET /garage; upserts sectors, spots, and gates |
| `PricingService` | Accepts occupancy ratio, returns BigDecimal multiplier (0.90/1.00/1.10/1.25) |
| `BillingService` | Accepts duration minutes + basePrice + multiplier; returns BigDecimal amount |
| `SectorService` | Computes occupancy via active sessions; checks capacity; reserves spot (PHYSICAL) |
| `WebhookService` | Orchestrates event processing; dispatches PHYSICAL vs LOGICAL logic |

### Data Flow: ENTRY Event (PHYSICAL)

```
WebhookController.handleWebhook(ENTRY, gateId)
  → WebhookService.processEntry(licensePlate, entryTime, gateId)
      → check active session → idempotency guard
      → SectorService.isGarageFull() [active sessions >= total capacity] → 409 if full
      → SectorService.getOccupancyRatio() [active sessions / total capacity]
      → PricingService.getMultiplier(ratio) → multiplier
      → SectorService.reserveAvailableSpot(sector?) → spot (pessimistic lock)
      → VehicleSessionRepository.save(session with spot, sector from spot, gateId)
```

### Data Flow: ENTRY Event (LOGICAL)

```
WebhookController.handleWebhook(ENTRY, gateId)
  → WebhookService.processEntry(licensePlate, entryTime, gateId)
      → check active session → idempotency guard
      → ParkingGateRepository.findById(gateId) → gate → sector
      → check active sessions in sector >= sector.maxCapacity → 409 if full
      → SectorService.getOccupancyRatio() → PricingService.getMultiplier → multiplier
      → VehicleSessionRepository.save(session without spot, sector from gate, gateId)
```

### Data Flow: EXIT Event

```
WebhookController.handleWebhook(EXIT)
  → WebhookService.processExit(licensePlate, exitTime)
      → VehicleSessionRepository.findActiveByPlate() → session
      → BillingService.calculate(entryTime, exitTime, basePrice, multiplier) → amount
      → session.amountCharged = amount, session.status = EXITED
      → if PHYSICAL and spot != null: spot.occupied = false
      → VehicleSessionRepository.save(session)  [transactional]
```

## Complexity Tracking

> No constitution violations. No complexity justification required.
