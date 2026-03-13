# Data Model: Parking Management System

**Feature**: 001-parking-management
**Date**: 2026-03-10 | **Updated**: 2026-03-11

## Overview

The system persists five core entities. The relationship chain is:
`GarageSector` ← `ParkingGate` (cancelas)
`GarageSector` ← `ParkingSpot` ← `VehicleSession`

Revenue is computed on demand from closed `VehicleSession` records
(no separate aggregation table required at this scope).

---

## Entity: GarageSector

Represents a logical partition of the garage as configured by the simulator.

| Column         | Type                        | Constraints                   | Notes                                     |
|----------------|-----------------------------|-------------------------------|------------------------------------------|
| `sector`       | VARCHAR(10)                 | PRIMARY KEY                   | Sector code, e.g. "A", "B"              |
| `base_price`   | DECIMAL(10, 2)              | NOT NULL                      | Hourly rate in BRL                       |
| `max_capacity` | INT                         | NOT NULL, > 0                 | Maximum number of vehicles allowed       |
| `control_type` | ENUM('PHYSICAL','LOGICAL')  | NOT NULL, DEFAULT 'PHYSICAL'  | How occupancy is controlled              |

**Business rules**:
- `PHYSICAL`: a specific `ParkingSpot` is reserved per vehicle on ENTRY; spot GPS is used
  to associate the session on PARKED.
- `LOGICAL`: only vehicle count is tracked against `max_capacity`; no physical spot is
  reserved; the gate determines the sector at ENTRY.
- Records are upserted on every application startup from the simulator.

---

## Entity: ParkingGate

Represents a cancela (barrier) in the garage. Used to determine which sector a vehicle
belongs to, especially for LOGICAL-controlled sectors.

| Column      | Type                         | Constraints                         | Notes                                   |
|-------------|------------------------------|-------------------------------------|-----------------------------------------|
| `id`        | VARCHAR(20)                  | PRIMARY KEY                         | External id from simulator, e.g. "G1"  |
| `sector`    | VARCHAR(10)                  | FK → GarageSector(sector), NOT NULL | Sector this gate belongs to             |
| `gate_type` | ENUM('ENTRY','EXIT','BOTH')  | NOT NULL                            | Whether gate is for entry, exit or both |

**Business rules**:
- On ENTRY events with `gate_id`, the system looks up the gate to find its sector.
- For LOGICAL sectors, the gate is the sole source of sector information at ENTRY time.
- For PHYSICAL sectors, the gate is informational (sector is also determined by the reserved spot).
- Records are upserted on application startup.

---

## Entity: ParkingSpot

Represents a physical parking space. Belongs to exactly one `GarageSector`.
Only relevant for PHYSICAL-controlled sectors.

| Column     | Type            | Constraints                      | Notes                          |
|------------|-----------------|----------------------------------|--------------------------------|
| `id`       | BIGINT          | PRIMARY KEY (simulator-provided) | Stable external ID             |
| `sector`   | VARCHAR(10)     | FK → GarageSector(sector)        | Logical group this spot is in  |
| `lat`      | DECIMAL(10, 8)  | NOT NULL                         | GPS latitude                   |
| `lng`      | DECIMAL(11, 8)  | NOT NULL                         | GPS longitude                  |
| `occupied` | BOOLEAN         | NOT NULL, DEFAULT FALSE          | True when a vehicle is parked  |

**Business rules**:
- `occupied` is set to `true` on ENTRY (PHYSICAL), `false` on EXIT.
- Used for physical spot locking via pessimistic write lock.
- Records are upserted on application startup (never deleted mid-operation).
- Spots belonging to LOGICAL sectors exist in the DB but are not used for session assignment.

---

## Entity: VehicleSession

Tracks one vehicle's complete stay from entry to exit.

| Column              | Type            | Constraints                         | Notes                                            |
|---------------------|-----------------|-------------------------------------|--------------------------------------------------|
| `id`                | BIGINT          | PRIMARY KEY, AUTO_INCREMENT         | Internal surrogate key                           |
| `license_plate`     | VARCHAR(20)     | NOT NULL                            | Vehicle identifier from event                    |
| `entry_time`        | DATETIME        | NOT NULL                            | From ENTRY event payload                         |
| `exit_time`         | DATETIME        | NULLABLE                            | From EXIT event payload; null while open         |
| `spot_id`           | BIGINT          | FK → ParkingSpot(id), NULLABLE      | Set on PARKED (PHYSICAL only); null for LOGICAL  |
| `sector`            | VARCHAR(10)     | FK → GarageSector(sector), NOT NULL | Set at ENTRY from spot (PHYSICAL) or gate (LOGICAL) |
| `gate_id`           | VARCHAR(20)     | FK → ParkingGate(id), NULLABLE      | Gate used at entry; null if not provided         |
| `price_multiplier`  | DECIMAL(4, 2)   | NOT NULL                            | Computed from garage occupancy at ENTRY          |
| `amount_charged`    | DECIMAL(10, 2)  | NULLABLE                            | Computed and set on EXIT                         |
| `status`            | ENUM            | NOT NULL                            | ENTERING → PARKED → EXITED                       |

**Unique constraint**: `(license_plate)` WHERE `status IN ('ENTERING', 'PARKED')` — only one
active session per plate at a time. Enforced at application level; the DB can use a
partial unique index on `license_plate` filtered by non-EXITED status.

**Business rules**:
- Session is created on ENTRY with `status = ENTERING`.
- On PARKED (PHYSICAL sector): `spot_id` updated to GPS-matched spot, `status = PARKED`.
- On PARKED (LOGICAL sector): `status = PARKED`, no spot update.
- On EXIT: `exit_time` set, `amount_charged` computed, `status = EXITED`, spot released (PHYSICAL only).
- `price_multiplier` is computed once at ENTRY and never updated afterward.

---

## State Machine: VehicleSession

```
[ENTRY event received]
        │
        ▼
   status = ENTERING
   PHYSICAL: spot tentatively occupied; sector from spot
   LOGICAL:  no spot assigned; sector from gate
   gate_id recorded; price_multiplier set
        │
[PARKED event received]
        │
        ▼
   status = PARKED
   PHYSICAL: spot_id = GPS-matched spot
   LOGICAL:  no-op (event accepted, no spot lookup)
        │
[EXIT event received]
        │
        ▼
   status = EXITED
   exit_time set
   amount_charged computed
   PHYSICAL: spot.occupied = false
   LOGICAL:  no spot to release
```

---

## Occupancy Calculation

Occupancy is computed **uniformly** using **active sessions** (status `ENTERING` or `PARKED`),
regardless of control type. This unifies the metric for both PHYSICAL and LOGICAL sectors.

```
occupancy_ratio = COUNT(active sessions) / SUM(max_capacity across all sectors)
```

| Ratio           | Multiplier | Tier   |
|-----------------|------------|--------|
| < 0.25          | 0.90       | LOW    |
| 0.25 ≤ r < 0.50 | 1.00       | NORMAL |
| 0.50 ≤ r < 0.75 | 1.10       | HIGH   |
| 0.75 ≤ r < 1.00 | 1.25       | PEAK   |
| r = 1.00        | BLOCKED    | FULL   |

---

## Billing Formula

```
duration_minutes = exit_time − entry_time (in minutes)

IF duration_minutes <= 30:
    amount = 0.00

ELSE:
    billable_hours = CEILING(duration_minutes / 60)
    amount = base_price × price_multiplier × billable_hours
```

`base_price` comes from the `GarageSector` linked to the session at EXIT time.

---

## Revenue Query

No separate aggregation table. Revenue is computed by:

```sql
SELECT SUM(amount_charged)
FROM vehicle_session
WHERE sector = :sector
  AND DATE(exit_time) = :date
  AND status = 'EXITED'
```

---

## Schema Migration Order (Flyway)

1. `V1__create_garage_sector.sql` — GarageSector table
2. `V2__create_parking_spot.sql` — ParkingSpot table (FK to GarageSector)
3. `V3__create_vehicle_session.sql` — VehicleSession table (FK to ParkingSpot, GarageSector)
4. `V4__add_gates_and_control_type.sql` — ParkingGate table; `control_type` on GarageSector;
   `gate_id` on VehicleSession
