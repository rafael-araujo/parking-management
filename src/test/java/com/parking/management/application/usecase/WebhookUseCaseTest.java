package com.parking.management.application.usecase;

import com.parking.management.application.exception.ConflictException;
import com.parking.management.application.exception.InvalidEventException;
import com.parking.management.application.exception.ResourceNotFoundException;
import com.parking.management.application.usecase.impl.WebhookUseCaseImpl;
import com.parking.management.domain.model.ControlTypeDTO;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.domain.service.BillingService;
import com.parking.management.domain.service.PricingService;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import com.parking.management.infrastructure.persistence.entity.VehicleSessionEntity;
import com.parking.management.infrastructure.persistence.repository.ParkingGateRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookUseCaseTest {

    @Mock private SectorUseCase sectorUseCase;
    @Mock private PricingService pricingService;
    @Mock private BillingService billingService;
    @Mock private VehicleSessionRepository sessionRepository;
    @Mock private ParkingSpotRepository spotRepository;
    @Mock private ParkingGateRepository gateRepository;

    private WebhookUseCase webhookUseCase;

    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2025, 1, 1, 12, 0);
    private static final LocalDateTime EXIT_TIME  = LocalDateTime.of(2025, 1, 1, 13, 0);

    private GarageSectorEntity sector;
    private ParkingSpotEntity spot;

    @BeforeEach
    void setUp() {
        webhookUseCase = new WebhookUseCaseImpl(
                sectorUseCase, pricingService, billingService,
                sessionRepository, spotRepository, gateRepository);

        sector = new GarageSectorEntity("A", new BigDecimal("10.00"), 10);
        sector.setControlType(ControlTypeDTO.PHYSICAL);

        spot = new ParkingSpotEntity();
        spot.setId(1L);
        spot.setSector(sector);
        spot.setOccupied(false);
    }

    // --- processEntry ---

    @Test
    void processEntry_whenEntryTimeNull_throwsInvalidEventException() {
        assertThatThrownBy(() -> webhookUseCase.processEntry("ZUL0001", null, null))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("entry_time is required");
    }

    @Test
    void processEntry_whenDuplicateActiveSession_isIgnored() {
        VehicleSessionEntity existing = new VehicleSessionEntity();
        existing.setLicensePlate("ZUL0001");
        existing.setStatus(SessionStatusDTO.ENTERING);

        when(sessionRepository.findByLicensePlateAndStatusIn(eq("ZUL0001"), any()))
                .thenReturn(List.of(existing));

        webhookUseCase.processEntry("ZUL0001", ENTRY_TIME, null);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void processEntry_whenGarageFull_throwsConflictException() {
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of());
        when(sectorUseCase.isGarageFull()).thenReturn(true);

        assertThatThrownBy(() -> webhookUseCase.processEntry("ZUL0001", ENTRY_TIME, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Garage is full");
    }

    @Test
    void processEntry_physical_createsSessionAndReservesSpot() {
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of());
        when(sectorUseCase.isGarageFull()).thenReturn(false);
        when(sectorUseCase.getOccupancyRatio()).thenReturn(new BigDecimal("0.10"));
        when(pricingService.getMultiplier(any())).thenReturn(new BigDecimal("0.90"));
        when(sectorUseCase.reserveAvailableSpot()).thenReturn(spot);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookUseCase.processEntry("ZUL0001", ENTRY_TIME, null);

        ArgumentCaptor<VehicleSessionEntity> captor = ArgumentCaptor.forClass(VehicleSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        VehicleSessionEntity saved = captor.getValue();
        assertThat(saved.getLicensePlate()).isEqualTo("ZUL0001");
        assertThat(saved.getStatus()).isEqualTo(SessionStatusDTO.ENTERING);
        assertThat(saved.getPriceMultiplier()).isEqualByComparingTo("0.90");
    }

    // --- processParked ---

    @Test
    void processParked_whenNoActiveSession_throwsResourceNotFoundException() {
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> webhookUseCase.processParked("ZUL0001", null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active session found");
    }

    @Test
    void processParked_whenLatLngMissing_throwsInvalidEventException() {
        VehicleSessionEntity session = activeSession(SessionStatusDTO.ENTERING);
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of(session));

        assertThatThrownBy(() -> webhookUseCase.processParked("ZUL0001", null, null))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("lat and lng are required");
    }

    @Test
    void processParked_whenNoSpotFoundNearCoordinates_throwsInvalidEventException() {
        VehicleSessionEntity session = activeSession(SessionStatusDTO.ENTERING);
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of(session));

        BigDecimal lat = new BigDecimal("-23.999999");
        BigDecimal lng = new BigDecimal("-46.999999");
        when(spotRepository.findNearestToCoordinates(lat, lng)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> webhookUseCase.processParked("ZUL0001", lat, lng))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("No parking spot found near coordinates");
    }

    @Test
    void processParked_physical_updatesSessionToParked() {
        VehicleSessionEntity session = activeSession(SessionStatusDTO.ENTERING);
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of(session));

        BigDecimal lat = new BigDecimal("-23.561684");
        BigDecimal lng = new BigDecimal("-46.655981");
        spot.setLat(lat);
        spot.setLng(lng);
        when(spotRepository.findNearestToCoordinates(lat, lng)).thenReturn(Optional.of(spot));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(spotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookUseCase.processParked("ZUL0001", lat, lng);

        ArgumentCaptor<VehicleSessionEntity> captor = ArgumentCaptor.forClass(VehicleSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatusDTO.PARKED);
    }

    // --- processExit ---

    @Test
    void processExit_whenExitTimeNull_throwsInvalidEventException() {
        assertThatThrownBy(() -> webhookUseCase.processExit("ZUL0001", null))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("exit_time is required");
    }

    @Test
    void processExit_whenNoActiveSession_throwsResourceNotFoundException() {
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> webhookUseCase.processExit("ZUL0001", EXIT_TIME))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("No active session found");
    }

    @Test
    void processExit_calculatesAmountAndClosesSession() {
        VehicleSessionEntity session = activeSession(SessionStatusDTO.PARKED);
        session.setEntryTime(ENTRY_TIME);
        when(sessionRepository.findByLicensePlateAndStatusIn(any(), any())).thenReturn(List.of(session));
        when(billingService.calculate(any(), any(), any(), any())).thenReturn(new BigDecimal("9.00"));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        webhookUseCase.processExit("ZUL0001", EXIT_TIME);

        ArgumentCaptor<VehicleSessionEntity> captor = ArgumentCaptor.forClass(VehicleSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        VehicleSessionEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SessionStatusDTO.EXITED);
        assertThat(saved.getAmountCharged()).isEqualByComparingTo("9.00");
        assertThat(saved.getExitTime()).isEqualTo(EXIT_TIME);
    }

    // --- helpers ---

    private VehicleSessionEntity activeSession(SessionStatusDTO status) {
        VehicleSessionEntity session = new VehicleSessionEntity();
        session.setLicensePlate("ZUL0001");
        session.setEntryTime(ENTRY_TIME);
        session.setSector(sector);
        session.setPriceMultiplier(new BigDecimal("1.00"));
        session.setStatus(status);
        return session;
    }
}
