# Feature Specification: Parking Management System

**Feature Branch**: `001-parking-management`
**Created**: 2026-03-10
**Status**: Draft
**Input**: Parking Management System

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Garage Initialization on Startup (Priority: P1)

When the system starts, it fetches the garage configuration (sectors and spots) from the
simulator and persists it locally. This is the prerequisite for all other functionality.

**Why this priority**: Without the garage layout in the system, no events can be processed
correctly. Every other user story depends on this foundation.

**Independent Test**: Start the application and verify that sectors and spots are stored
and queryable. No other feature needs to be active.

**Acceptance Scenarios**:

1. **Given** the simulator is running with a garage configuration, **When** the application
   starts, **Then** all sectors (with base price and max capacity) and all spots (with id,
   sector, lat/lng) are persisted in the system.
2. **Given** the application has already started once, **When** it restarts, **Then** the
   existing garage data is updated/refreshed from the simulator without duplicating records.
3. **Given** the simulator is unavailable at startup, **When** the application attempts to
   fetch garage data, **Then** the application either retries or fails fast with a clear
   error — it does NOT start silently with no garage data.

---

### User Story 2 - Vehicle Entry and Spot Assignment (Priority: P1)

When a vehicle arrives at the garage, the system receives an ENTRY event and marks a spot
in the corresponding sector as occupied. The dynamic pricing rate applicable to this session
is captured at the moment of entry based on current sector occupancy.

**Why this priority**: This is the starting point of every vehicle session. Without it,
no parking session can be tracked or billed.

**Independent Test**: POST an ENTRY event for a license plate, then verify a spot in the
correct sector is marked occupied and the session is recorded with the correct price tier.

**Acceptance Scenarios**:

1. **Given** a sector with available spots, **When** an ENTRY event is received for a
   vehicle, **Then** one spot in the sector is marked occupied, a session is created with
   entry time and the price multiplier computed from current occupancy, and HTTP 200 is
   returned.
2. **Given** a sector at 100% occupancy, **When** an ENTRY event is received, **Then**
   the system returns HTTP 409 (Conflict) and no session is created.
3. **Given** sector occupancy is below 25%, **When** an ENTRY event is received, **Then**
   a 10% discount multiplier (0.90) is recorded on the session.
4. **Given** sector occupancy is between 25% and 50% (inclusive), **When** an ENTRY event
   arrives, **Then** no discount or surcharge is applied (multiplier 1.00).
5. **Given** sector occupancy is between 50% and 75% (inclusive), **When** an ENTRY event
   arrives, **Then** a 10% surcharge multiplier (1.10) is recorded.
6. **Given** sector occupancy is between 75% and 100% (exclusive), **When** an ENTRY event
   arrives, **Then** a 25% surcharge multiplier (1.25) is recorded.

---

### User Story 3 - PARKED Event: Spot Association by Location (Priority: P2)

After a vehicle is assigned a spot on entry, it sends a PARKED event with GPS coordinates.
The system associates the vehicle session with the nearest spot matching those coordinates.

**Why this priority**: The PARKED event refines the spot assignment from "any available spot
in the sector" to the specific physical spot where the vehicle is located.

**Independent Test**: POST a PARKED event for an active session and verify the session is
linked to the spot closest to the provided coordinates.

**Acceptance Scenarios**:

1. **Given** an active session for a license plate, **When** a PARKED event is received
   with lat/lng, **Then** the system finds the spot whose coordinates match (or are
   nearest to) the provided location and links it to the session.
2. **Given** a PARKED event arrives with no prior ENTRY session for that license plate,
   **When** processed, **Then** the system returns HTTP 200 and logs a warning (the event
   is accepted but no session update occurs).

---

### User Story 4 - Vehicle Exit and Billing (Priority: P1)

When a vehicle leaves the garage, the system receives an EXIT event, releases the occupied
spot, calculates the parking fee using the price multiplier captured at entry, and records
the revenue.

**Why this priority**: Billing is the core business output of the system. Exit processing
closes the vehicle session and generates revenue data.

**Independent Test**: Send ENTRY then EXIT events for the same license plate and verify the
spot is released, the correct fee is calculated, and the revenue record is persisted.

**Acceptance Scenarios**:

1. **Given** an active session with entry time T, **When** an EXIT event arrives at time
   T + 20 min (≤ 30 min), **Then** the fee is R$0.00 (free period), the spot is released,
   and the session is closed.
2. **Given** an active session with entry time T, **When** an EXIT event arrives at time
   T + 31 min (first charged minute), **Then** the fee equals basePrice × multiplier × 1
   hour (ceiling of 31/60 = 1).
3. **Given** an active session with entry time T, **When** an EXIT event arrives at time
   T + 90 min, **Then** the fee equals basePrice × multiplier × 2 hours (ceiling of 90/60 = 2).
4. **Given** a completed session, **When** EXIT is processed, **Then** the spot is marked
   available and a revenue record is persisted with the amount, sector, and exit date.
5. **Given** an EXIT event for a license plate with no active session, **When** processed,
   **Then** the system returns HTTP 200 and logs a warning.

---

### User Story 5 - Revenue Query by Sector and Date (Priority: P2)

An operator can query the total revenue collected for a specific sector on a specific date.

**Why this priority**: Revenue reporting is the primary business intelligence output.
It enables the garage operator to track earnings per sector per day.

**Independent Test**: Complete several vehicle sessions on a given date for a given sector,
then call GET /revenue with that sector and date and verify the total matches the sum of
all fees billed.

**Acceptance Scenarios**:

1. **Given** completed sessions with fees billed on 2025-01-01 in sector "A", **When**
   GET /revenue is called with date="2025-01-01" and sector="A", **Then** the response
   returns the sum of all fees for that sector+date, with currency "BRL" and an ISO-8601
   timestamp.
2. **Given** no completed sessions for a sector/date combination, **When** GET /revenue
   is queried, **Then** the response returns amount 0.00.
3. **Given** an invalid date format or missing sector, **When** GET /revenue is called,
   **Then** the system returns HTTP 400 with a descriptive error message.

---

### Edge Cases

- What happens when a PARKED event arrives before ENTRY for the same plate? System accepts
  the event (HTTP 200) but takes no action, logs a warning.
- What happens when EXIT arrives before PARKED? Session is closed using the spot assigned
  at ENTRY; no PARKED data required to complete billing.
- What happens when two ENTRY events arrive for the same license plate concurrently?
  Only the first is processed; the second is rejected or deduplicated.
- What happens when the garage has multiple sectors with different base prices? Each sector
  is evaluated independently for occupancy and pricing.
- What happens at exactly 30 minutes (the free/paid boundary)? Sessions of exactly 30 min
  are free; sessions of 30 min + 1 second (rounded up to 1 hour) are charged one hour.
- What if the simulator sends a garage configuration with zero spots? The system persists
  the configuration and immediately treats all sectors as fully occupied (no entries allowed).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST fetch garage configuration (sectors and spots) from the simulator
  on startup and persist the data.
- **FR-002**: System MUST expose a webhook endpoint that accepts ENTRY, PARKED, and EXIT
  events via HTTP POST and returns HTTP 200 for each.
- **FR-003**: System MUST, on ENTRY, mark a spot in the relevant sector as occupied and
  record the session with entry time and occupancy-based price multiplier.
- **FR-004**: System MUST reject ENTRY events for sectors at 100% capacity with HTTP 409.
- **FR-005**: System MUST apply dynamic pricing at the moment of ENTRY based on sector
  occupancy tiers: <25% → 0.90×, <50% → 1.00×, <75% → 1.10×, ≤100% → 1.25×.
- **FR-006**: System MUST, on PARKED, associate the vehicle session with the spot matching
  the provided GPS coordinates.
- **FR-007**: System MUST, on EXIT, release the spot, calculate the fee using the stored
  price multiplier, and persist a revenue record.
- **FR-008**: Billing MUST be: R$0.00 for stays ≤ 30 minutes; basePrice × multiplier ×
  ⌈minutes/60⌉ for stays > 30 minutes (ceiling applied to hours).
- **FR-009**: System MUST expose GET /revenue accepting a date and sector, returning total
  revenue for that sector on that date with currency "BRL" and an ISO-8601 timestamp.
- **FR-010**: System MUST process webhook events idempotently — re-delivery of the same
  event MUST NOT create duplicate sessions or double-charge.
- **FR-011**: Each `GarageSector` MUST declare a `control_type`: `PHYSICAL` (reserves a
  specific physical spot per vehicle) or `LOGICAL` (tracks only vehicle count vs capacity,
  no physical spot assignment).
- **FR-012**: System MUST fetch and persist `ParkingGate` (cancela) records from the
  simulator on startup. Each gate has an id, an associated sector, and a type (ENTRY/EXIT/BOTH).
- **FR-013**: All webhook events MUST support an optional `gate_id` field. For LOGICAL-controlled
  sectors, `gate_id` is required on ENTRY to determine the sector. For PHYSICAL sectors, `gate_id`
  is informational and recorded on the session.
- **FR-014**: For LOGICAL-controlled sectors, the PARKED event MUST be accepted (HTTP 200)
  but MUST NOT attempt physical spot assignment (no GPS → spot lookup).
- **FR-015**: Garage occupancy for dynamic pricing MUST be computed as the count of active
  sessions (status ENTERING or PARKED) divided by total max_capacity across all sectors,
  unifying the metric for both control types.

### Key Entities

- **GarageSector**: Represents a logical division of the garage. Attributes: sector code,
  base hourly price, maximum vehicle capacity, control type (PHYSICAL or LOGICAL).
- **ParkingSpot**: A physical parking space within a sector. Attributes: unique id, sector,
  GPS coordinates (lat/lng), occupancy status. Only used for PHYSICAL-controlled sectors.
- **ParkingGate**: A cancela (entrance/exit barrier). Attributes: id (external), sector FK,
  gate type (ENTRY, EXIT, BOTH). Determines the sector for LOGICAL-controlled entries.
- **VehicleSession**: Tracks a single vehicle's stay. Attributes: license plate, entry time,
  exit time, assigned spot (null for LOGICAL sectors), sector, price multiplier applied at
  entry, final amount charged, gate id used at entry.
- **RevenueRecord**: Aggregated or per-session billing record used for reporting. Attributes:
  sector, date, amount billed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All three webhook event types (ENTRY, PARKED, EXIT) are correctly processed
  end-to-end with no data loss across a full simulated session.
- **SC-002**: Sector capacity enforcement is 100% accurate — no vehicle enters a sector
  at 100% occupancy; the first available entry after a release succeeds immediately.
- **SC-003**: Fee calculations are correct for all pricing boundary conditions: 30-min
  free boundary, first charged hour, multi-hour stays, and all four occupancy tiers.
- **SC-004**: Revenue totals returned by GET /revenue exactly match the sum of all fees
  billed for that sector and date.
- **SC-005**: Duplicate/re-delivered webhook events produce no additional sessions or
  revenue entries.
- **SC-006**: Application startup completes with garage data populated before accepting
  any webhook events.

## Assumptions

- The simulator is the authoritative source of garage configuration; the system trusts
  the data returned by GET /garage without validation.
- The simulator sends events in chronological order per vehicle (ENTRY before PARKED
  before EXIT); out-of-order events are handled gracefully but are not the primary flow.
- "Garage occupancy" at the time of ENTRY is computed as active sessions (ENTERING + PARKED)
  divided by total max_capacity across all sectors. This metric is consistent for both
  PHYSICAL and LOGICAL control types.
- For PHYSICAL sectors, the vehicle's sector is determined at ENTRY time by the reserved spot.
  For LOGICAL sectors, the sector is determined at ENTRY time by the gate (gate → sector).
- A PARKED event for a LOGICAL-controlled sector is silently accepted and ignored (no spot lookup).
- Revenue reporting uses the exit date (not entry date) to group sessions into daily totals.
- The application runs on a single node; no distributed coordination is required.
