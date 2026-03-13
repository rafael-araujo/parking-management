# parking-management-2 Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-03-11

## Active Technologies

- Java 21 (LTS) + Spring Boot 3.x, Spring Data JPA (Hibernate), Spring Web (001-parking-management)

## Architecture

This project follows **Clean Architecture** with four layers:

| Layer | Package | Contents |
|---|---|---|
| adapter | `adapter/controller/` | REST controllers |
| adapter | `adapter/model/request/` | HTTP request objects |
| adapter | `adapter/model/response/` | HTTP response objects + ErrorResponse |
| application | `application/usecase/` | Use-case interfaces (WebhookUseCase, RevenueUseCase, SectorUseCase) |
| application | `application/usecase/impl/` | Use-case implementations (WebhookUseCaseImpl, RevenueUseCaseImpl, SectorUseCaseImpl) |
| application | `application/mapper/` | Object converters (request↔dto, dto↔entity, entity↔dto, dto↔response) |
| application | `application/exception/` | GlobalExceptionHandler |
| domain | `domain/model/` | Domain DTOs + enums (inter-layer data carriers) |
| domain | `domain/service/` | Business logic interfaces (BillingService, PricingService) |
| domain | `domain/service/impl/` | Business logic implementations (BillingServiceImpl, PricingServiceImpl) |
| infrastructure | `infrastructure/persistence/entity/` | JPA @Entity classes |
| infrastructure | `infrastructure/persistence/repository/` | Spring Data JPA repositories |
| infrastructure | `infrastructure/client/` | Simulator API client |
| infrastructure | `infrastructure/config/` | Spring beans |

**Dependency rule**: `adapter` → `application` → `domain` ← `infrastructure`

**Object boundaries**:
- `request`/`response` only in `adapter/controller/`
- `entity` only in `infrastructure/persistence/` and `application/mapper/`
- `dto` (domain model) for communication between layers

## Project Structure

```text
src/main/java/com/parking/management/
  ParkingManagementApplication.java
  adapter/
    controller/
      WebhookController.java, RevenueController.java
    model/
      request/  WebhookEventRequest.java, RevenueRequest.java
      response/ RevenueResponse.java, ErrorResponse.java
  application/
    usecase/    WebhookUseCase.java, RevenueUseCase.java, SectorUseCase.java (interfaces)
      impl/     WebhookUseCaseImpl.java, RevenueUseCaseImpl.java, SectorUseCaseImpl.java
    mapper/     RevenueMapper.java
    exception/  GlobalExceptionHandler.java
  domain/
    model/      RevenueDTO.java, SessionStatusDTO.java, ControlTypeDTO.java, GateTypeDTO.java, SimulatorGarageDTO.java
    service/    BillingService.java, PricingService.java (interfaces)
      impl/     BillingServiceImpl.java, PricingServiceImpl.java
  infrastructure/
    config/     AppConfig.java
    persistence/
      entity/     GarageSectorEntity.java, ParkingSpotEntity.java, ParkingGateEntity.java, VehicleSessionEntity.java
      repository/ GarageSectorRepository.java, ParkingSpotRepository.java
                  ParkingGateRepository.java, VehicleSessionRepository.java
    client/     SimulatorClient.java
src/main/resources/
  db/migration/  Flyway SQL migrations (V1–V4)
  application.yml
src/test/java/   Unit and integration tests (mirror source package structure)
```

## Commands

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test
```

## Code Style

- Java 21 (LTS): follow standard Java conventions
- Use `BigDecimal` for all monetary/pricing values (never `double`)
- Follow Clean Architecture object boundaries (see Architecture section)
- Database mutations (ENTRY/EXIT) MUST be annotated `@Transactional`
- All errors MUST use the standard `ErrorResponse` format via `GlobalExceptionHandler`

## Recent Changes

- 001-parking-management: Added Java 21 (LTS) + Spring Boot 3.x, Spring Data JPA (Hibernate), Spring Web

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
