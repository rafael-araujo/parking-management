# Contract: Simulator Client (Outbound)

**Direction**: System → Simulator (outbound HTTP call at startup)
**Endpoint**: `GET http://localhost:3000/garage`
**Content-Type**: `application/json`

This call is made once during application startup via an `ApplicationRunner`.
The system persists the returned configuration before accepting any webhook events.

---

## Response from Simulator

```json
{
  "garage": [
    {
      "sector": "A",
      "basePrice": 10.0,
      "max_capacity": 10,
      "control_type": "PHYSICAL"
    },
    {
      "sector": "C",
      "basePrice": 20.0,
      "max_capacity": 10,
      "control_type": "LOGICAL"
    }
  ],
  "spots": [
    {
      "id": 1,
      "sector": "A",
      "lat": -23.561684,
      "lng": -46.655981
    }
  ],
  "gates": [
    {
      "id": "G1",
      "sector": "A",
      "type": "ENTRY"
    },
    {
      "id": "G2",
      "sector": "A",
      "type": "EXIT"
    },
    {
      "id": "G5",
      "sector": "C",
      "type": "BOTH"
    }
  ]
}
```

### `garage` array — Sector configurations

| Field          | Type             | Description                                    |
|----------------|------------------|------------------------------------------------|
| `sector`       | string           | Sector code, used as primary key               |
| `basePrice`    | number           | Hourly base price in BRL                       |
| `max_capacity` | int              | Maximum number of vehicles in sector           |
| `control_type` | string           | `"PHYSICAL"` or `"LOGICAL"`. Defaults to `"PHYSICAL"` if absent |

### `spots` array — Individual parking spots

| Field    | Type   | Description                                  |
|----------|--------|----------------------------------------------|
| `id`     | int    | Stable spot identifier (used as primary key) |
| `sector` | string | Which sector this spot belongs to            |
| `lat`    | number | GPS latitude                                 |
| `lng`    | number | GPS longitude                                |

### `gates` array — Cancelas (barriers)

| Field    | Type   | Description                                              |
|----------|--------|----------------------------------------------------------|
| `id`     | string | Stable gate identifier, e.g. `"G1"`                     |
| `sector` | string | Which sector this gate belongs to                        |
| `type`   | string | `"ENTRY"`, `"EXIT"`, or `"BOTH"`                        |

---

## Startup Behavior

1. Application calls `GET /garage` on the simulator.
2. For each entry in `garage`: upsert `GarageSector` (insert or update if exists), including `control_type`.
3. For each entry in `spots`: upsert `ParkingSpot` (insert or update if exists).
4. For each entry in `gates`: upsert `ParkingGate` (insert or update if exists).
5. Spots already marked `occupied = true` retain their occupancy status on restart.
6. If `gates` array is absent from the response, log a warning and continue (backwards compatibility).
7. If the call fails (connection refused, non-200 response), the application MUST NOT
   start accepting webhook events — it should fail fast with a clear log error.

---

## Error Handling

| Scenario                        | Application Behavior                                           |
|---------------------------------|----------------------------------------------------------------|
| Simulator not running           | Log error, throw exception, application context fails to start |
| Non-200 HTTP response           | Log status + body, throw exception, application fails to start |
| Empty `garage` or `spots` array | Log warning, continue (edge case: valid empty garage)         |
| `gates` array absent or empty   | Log warning, continue (PHYSICAL sectors work without gates)   |
| Network timeout                 | Log error, application fails to start (no silent degradation)  |
