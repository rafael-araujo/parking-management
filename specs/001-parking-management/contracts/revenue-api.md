# Contract: Revenue Query API

**Endpoint**: `GET /revenue`
**Content-Type**: `application/json`

Returns the total revenue collected for a specific sector on a specific date.

---

## Request

Parameters are sent as a JSON request body (per the test specification).

```json
{
  "date": "2025-01-01",
  "sector": "A"
}
```

| Field    | Type   | Required | Format     | Description                          |
|----------|--------|----------|------------|--------------------------------------|
| `date`   | string | YES      | YYYY-MM-DD | The calendar date to query           |
| `sector` | string | YES      |            | The sector code to query (e.g. "A")  |

**Note**: Revenue is grouped by the date of `exit_time` (when billing occurred), not entry date.

---

## Responses

### 200 OK — Revenue found (including zero)

```json
{
  "amount": 150.00,
  "currency": "BRL",
  "timestamp": "2026-03-10T12:00:00.000Z"
}
```

| Field       | Type   | Description                                              |
|-------------|--------|----------------------------------------------------------|
| `amount`    | number | Total revenue in BRL; 0.00 if no completed sessions     |
| `currency`  | string | Always `"BRL"`                                           |
| `timestamp` | string | ISO-8601 UTC timestamp of when the response was generated|

### 400 Bad Request — Validation error

```json
{
  "message": "Invalid date format. Expected YYYY-MM-DD."
}
```

Returned when:
- `date` is missing or not in `YYYY-MM-DD` format.
- `sector` is missing or blank.

### 404 Not Found — Unknown sector

```json
{
  "message": "Sector 'Z' not found."
}
```

Returned when the provided `sector` does not exist in the system's garage configuration.

---

## Business Rules

- Only sessions with `status = 'EXITED'` are included in the total.
- Sessions where `amount_charged = 0.00` (free period ≤ 30 min) are included in the
  count but contribute 0.00 to the total.
- The `date` filter applies to the `exit_time` of the session (day in UTC).
- If no sessions match the criteria, `amount` is `0.00` (not 404).
