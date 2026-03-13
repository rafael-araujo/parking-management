package com.parking.management.application.usecase;

import com.parking.management.application.usecase.impl.SectorUseCaseImpl;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectorUseCaseTest {

    @Mock private ParkingSpotRepository spotRepository;
    @Mock private GarageSectorRepository sectorRepository;
    @Mock private VehicleSessionRepository sessionRepository;

    private SectorUseCase sectorUseCase;

    @BeforeEach
    void setUp() {
        sectorUseCase = new SectorUseCaseImpl(spotRepository, sectorRepository, sessionRepository);
    }

    @Test
    void isGarageFull_returnsTrueWhenActiveSessionsEqualCapacity() {
        when(sessionRepository.countByStatusIn(any())).thenReturn(100L);
        when(sectorRepository.sumTotalCapacity()).thenReturn(100L);

        assertThat(sectorUseCase.isGarageFull()).isTrue();
    }

    @Test
    void isGarageFull_returnsFalseWhenCapacityAvailable() {
        when(sessionRepository.countByStatusIn(any())).thenReturn(50L);
        when(sectorRepository.sumTotalCapacity()).thenReturn(100L);

        assertThat(sectorUseCase.isGarageFull()).isFalse();
    }

    @Test
    void getOccupancyRatio_computesCorrectly() {
        when(sessionRepository.countByStatusIn(any())).thenReturn(25L);
        when(sectorRepository.sumTotalCapacity()).thenReturn(100L);

        BigDecimal ratio = sectorUseCase.getOccupancyRatio();

        assertThat(ratio).isEqualByComparingTo("0.25");
    }

    @Test
    void getOccupancyRatio_zeroWhenNoCapacity() {
        when(sessionRepository.countByStatusIn(any())).thenReturn(0L);
        when(sectorRepository.sumTotalCapacity()).thenReturn(0L);

        BigDecimal ratio = sectorUseCase.getOccupancyRatio();

        assertThat(ratio).isEqualByComparingTo("0.00");
    }

    @Test
    void isSectorFull_returnsTrueWhenAtCapacity() {
        GarageSectorEntity sector = new GarageSectorEntity("A", new BigDecimal("10.00"), 5);
        when(sessionRepository.countBySectorAndStatusIn(sector, List.of(SessionStatusDTO.ENTERING, SessionStatusDTO.PARKED)))
                .thenReturn(5L);

        assertThat(sectorUseCase.isSectorFull(sector)).isTrue();
    }

    @Test
    void isSectorFull_returnsFalseWhenBelowCapacity() {
        GarageSectorEntity sector = new GarageSectorEntity("A", new BigDecimal("10.00"), 5);
        when(sessionRepository.countBySectorAndStatusIn(sector, List.of(SessionStatusDTO.ENTERING, SessionStatusDTO.PARKED)))
                .thenReturn(3L);

        assertThat(sectorUseCase.isSectorFull(sector)).isFalse();
    }

    @Test
    void reserveAvailableSpot_marksSpotOccupied() {
        GarageSectorEntity sector = new GarageSectorEntity("A", new BigDecimal("10.00"), 10);
        ParkingSpotEntity spot = new ParkingSpotEntity();
        spot.setId(1L);
        spot.setSector(sector);
        spot.setOccupied(false);

        when(spotRepository.findFirstByOccupiedFalseOrderByIdAsc()).thenReturn(Optional.of(spot));
        when(spotRepository.save(spot)).thenReturn(spot);

        ParkingSpotEntity reserved = sectorUseCase.reserveAvailableSpot();

        assertThat(reserved.isOccupied()).isTrue();
        verify(spotRepository).save(spot);
    }

    @Test
    void reserveAvailableSpot_whenFull_throws409() {
        when(spotRepository.findFirstByOccupiedFalseOrderByIdAsc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectorUseCase.reserveAvailableSpot())
                .hasMessageContaining("Garage is full");
    }

    @Test
    void reserveAvailableSpotInSector_marksSpotOccupied() {
        GarageSectorEntity sector = new GarageSectorEntity("A", new BigDecimal("10.00"), 10);
        ParkingSpotEntity spot = new ParkingSpotEntity();
        spot.setId(2L);
        spot.setSector(sector);
        spot.setOccupied(false);

        when(spotRepository.findFirstBySectorAndOccupiedFalseOrderByIdAsc(sector)).thenReturn(Optional.of(spot));
        when(spotRepository.save(spot)).thenReturn(spot);

        ParkingSpotEntity reserved = sectorUseCase.reserveAvailableSpotInSector(sector);

        assertThat(reserved.isOccupied()).isTrue();
        verify(spotRepository).save(spot);
    }

    @Test
    void reserveAvailableSpotInSector_whenFull_throws409() {
        GarageSectorEntity sector = new GarageSectorEntity("A", new BigDecimal("10.00"), 10);
        when(spotRepository.findFirstBySectorAndOccupiedFalseOrderByIdAsc(sector)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectorUseCase.reserveAvailableSpotInSector(sector))
                .hasMessageContaining("Sector is full");
    }
}
