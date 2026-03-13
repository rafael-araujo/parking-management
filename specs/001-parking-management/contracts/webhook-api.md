# Contract: Webhook API

**Endpoint**: `POST /webhook`
**Port**: 3003 (the port the simulator targets)
**Content-Type**: `application/json`

This endpoint receives all vehicle lifecycle events from the simulator. It is the
primary inbound interface of the system.

---

## Event: ENTRY

Signals that a vehicle has entered the garage through a gate (cancela).

### Request Body

```json
{
  "license_plate": "ZUL0001",
  "entry_time": "2025-01-01T12:00:00.000Z",
  "event_type": "ENTRY",
  "gate_id": "G1"
}
```

| Field           | Type   | Required | Description                                              |
|-----------------|--------|----------|----------------------------------------------------------|
| `license_plate` | string | YES      | Unique vehicle identifier                                |
| `entry_time`    | string | YES      | ISO-8601 UTC timestamp of entry                          |
| `event_type`    | string | YES      | Must be `"ENTRY"`                                        |
| `gate_id`       | string | NO*      | ID of the cancela used. Required for LOGICAL sectors.    |

> *`gate_id` is required when the sector uses LOGICAL control. For PHYSICAL sectors it is
> optional and recorded for traceability.

### Responses

| Status | Condition                                       | Body                                         |
|--------|-------------------------------------------------|----------------------------------------------|
| 200    | Session created; spot reserved (PHYSICAL) or count incremented (LOGICAL) | `{}`           |
| 409    | Sector (or garage) is at 100% occupancy         | `{"message": "Garage is full"}`              |
| 400    | Missing or malformed required fields            | `{"message": "<error description>"}`         |

### Side Effects

**PHYSICAL sector**:
- One available `ParkingSpot` in the sector is marked `occupied = true`.
- A `VehicleSession` is created with `status = ENTERING`, `entry_time` set,
  `sector` from the reserved spot, `gate_id` recorded, and `price_multiplier` computed.

**LOGICAL sector**:
- A `VehicleSession` is created with `status = ENTERING`, `entry_time` set,
  `sector` from the gate, `gate_id` recorded, and `price_multiplier` computed.
- No physical spot is reserved.

### Idempotency

If a session with the same `license_plate` already has status `ENTERING` or `PARKED`,
the event is silently accepted (HTTP 200) without creating a duplicate session.

---

## Event: PARKED

Signals that a vehicle has parked at a specific GPS location.

### Request Body

```json
{
  "license_plate": "ZUL0001",
  "lat": -23.561684,
  "lng": -46.655981,
  "event_type": "PARKED",
  "gate_id": "G1"
}
```

| Field           | Type   | Required | Description                                      |
|-----------------|--------|----------|--------------------------------------------------|
| `license_plate` | string | YES      | Must match an active session                     |
| `lat`           | number | YES      | GPS latitude of the parked vehicle               |
| `lng`           | number | YES      | GPS longitude of the parked vehicle              |
| `event_type`    | string | YES      | Must be `"PARKED"`                               |
| `gate_id`       | string | NO       | Informational; not used for spot lookup          |

### Responses

| Status | Condition                                              | Body                                 |
|--------|--------------------------------------------------------|--------------------------------------|
| 200    | Spot association updated (PHYSICAL), or accepted as no-op (LOGICAL) | `{}`          |
| 400    | Missing or malformed required fields                   | `{"message": "<error description>"}` |

### Side Effects

**PHYSICAL sector**:
- The `VehicleSession` for `license_plate` has its `spot_id` updated to the `ParkingSpot`
  whose coordinates are closest to the provided `lat`/`lng`.
- Session `status` is updated to `PARKED`.

**LOGICAL sector**:
- Session `status` is updated to `PARKED`.
- No GPS → spot lookup is performed.

### Idempotency

If the session is already `PARKED` or `EXITED`, the event is accepted without change.
If no active session exists for the plate, HTTP 200 is returned and a warning is logged.

---

## Event: EXIT

Signals that a vehicle has exited the garage through a gate (cancela).

### Request Body

```json
{
  "license_plate": "ZUL0001",
  "exit_time": "2025-01-01T12:00:00.000Z",
  "event_type": "EXIT",
  "gate_id": "G2"
}
```

| Field           | Type   | Required | Description                              |
|-----------------|--------|----------|------------------------------------------|
| `license_plate` | string | YES      | Must match an active session             |
| `exit_time`     | string | YES      | ISO-8601 UTC timestamp of exit           |
| `event_type`    | string | YES      | Must be `"EXIT"`                         |
| `gate_id`       | string | NO       | ID of the exit cancela (informational)   |

### Responses

| Status | Condition                                                 | Body                                 |
|--------|-----------------------------------------------------------|--------------------------------------|
| 200    | Session closed, billing computed, spot released (PHYSICAL)| `{}`                                 |
| 400    | Missing or malformed required fields                      | `{"message": "<error description>"}` |

### Side Effects

- `VehicleSession.exit_time` is set to `exit_time`.
- `VehicleSession.amount_charged` is computed using the billing formula.
- `VehicleSession.status` is updated to `EXITED`.
- **PHYSICAL only**: `ParkingSpot.occupied` is set to `false`.

### Idempotency

If the session is already `EXITED`, the event is accepted (HTTP 200) without reprocessing.
If no active session exists for the plate, HTTP 200 is returned and a warning is logged.

---

## Billing Formula (Reference)

```
duration_minutes = exit_time − entry_time (minutes, rounded down to integer)

IF duration_minutes <= 30:
    amount_charged = 0.00

ELSE:
    billable_hours = CEILING(duration_minutes / 60)
    amount_charged = base_price × price_multiplier × billable_hours
```

Where `base_price` comes from the sector of the session.
