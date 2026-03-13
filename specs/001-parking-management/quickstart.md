# Quickstart: Parking Management System

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- MySQL 8.0+
- Docker (to run the simulator)

---

## 1. Start the Simulator

```bash
docker run -d --network="host" cfontes0estapar/garage-sim:1.0.0
```

Verify the simulator is running and responding:

```bash
curl http://localhost:3000/garage
```

Expected: JSON with `garage` and `spots` arrays.

---

## 2. Set Up MySQL Database

```sql
CREATE DATABASE parking_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'parking'@'localhost' IDENTIFIED BY 'parking123';
GRANT ALL PRIVILEGES ON parking_management.* TO 'parking'@'localhost';
FLUSH PRIVILEGES;
```

---

## 3. Configure the Application

Edit `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/parking_management?useSSL=false&serverTimezone=UTC
    username: parking
    password: parking123
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true

server:
  port: 3003

simulator:
  base-url: http://localhost:3000
```

---

## 4. Build and Run

```bash
mvn clean package -DskipTests
java -jar target/parking-management-*.jar
```

Or with Maven directly:

```bash
mvn spring-boot:run
```

On startup the application will:
1. Call `GET /garage` on the simulator.
2. Persist all sectors and spots to the database.
3. Begin listening for webhook events on port 3003.

Expected startup log:
```
INFO  GarageInitializationService - Fetched X sectors and Y spots from simulator
INFO  Started ParkingManagementApplication in Z seconds
```

---

## 5. Verify the System

### Check webhook endpoint is live

```bash
curl -X POST http://localhost:3003/webhook \
  -H "Content-Type: application/json" \
  -d '{"license_plate":"TEST001","entry_time":"2025-01-01T12:00:00.000Z","event_type":"ENTRY"}'
```

Expected: HTTP 200

### Query revenue (should be 0)

```bash
curl -X GET http://localhost:3003/revenue \
  -H "Content-Type: application/json" \
  -d '{"date":"2025-01-01","sector":"A"}'
```

Expected:
```json
{"amount": 0.00, "currency": "BRL", "timestamp": "..."}
```

---

## 6. Run Tests

```bash
mvn test
```

Unit tests cover:
- `PricingServiceTest` — all four occupancy tiers
- `BillingServiceTest` — free period, first hour, multi-hour, boundary at 30 min
- `SectorServiceTest` — capacity enforcement (block at 100%, allow after exit)

Integration tests cover:
- Full ENTRY → PARKED → EXIT cycle
- Revenue query consistency

---

## Validation Checklist

- [ ] Simulator responds to `GET /garage`
- [ ] Application starts without errors
- [ ] Sectors and spots visible in database after startup
- [ ] ENTRY webhook returns HTTP 200
- [ ] ENTRY with full garage returns HTTP 409
- [ ] EXIT correctly bills and releases spot
- [ ] GET /revenue returns correct total after completed sessions
- [ ] Duplicate ENTRY event creates no duplicate session
