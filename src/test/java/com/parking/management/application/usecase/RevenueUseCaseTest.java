package com.parking.management.application.usecase;

import com.parking.management.application.exception.ResourceNotFoundException;
import com.parking.management.application.usecase.impl.RevenueUseCaseImpl;
import com.parking.management.domain.model.RevenueDTO;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueUseCaseTest {

    @Mock private VehicleSessionRepository sessionRepository;
    @Mock private GarageSectorRepository sectorRepository;

    private RevenueUseCase revenueUseCase;

    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);

    @BeforeEach
    void setUp() {
        revenueUseCase = new RevenueUseCaseImpl(sessionRepository, sectorRepository);
    }

    @Test
    void getRevenue_whenSectorNotFound_throwsResourceNotFoundException() {
        when(sectorRepository.existsById("Z")).thenReturn(false);

        assertThatThrownBy(() -> revenueUseCase.getRevenue("Z", DATE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sector 'Z' not found");
    }

    @Test
    void getRevenue_returnsSumForDate() {
        when(sectorRepository.existsById("A")).thenReturn(true);
        when(sessionRepository.sumRevenueBySectorAndDate(
                eq("A"), any(), any(), eq(SessionStatusDTO.EXITED)))
                .thenReturn(new BigDecimal("45.00"));

        RevenueDTO dto = revenueUseCase.getRevenue("A", DATE);

        assertThat(dto.getAmount()).isEqualByComparingTo("45.00");
        assertThat(dto.getCurrency()).isEqualTo("BRL");
        assertThat(dto.getTimestamp()).isNotNull();
    }

    @Test
    void getRevenue_returnsZeroWhenNoCompletedSessions() {
        when(sectorRepository.existsById("A")).thenReturn(true);
        when(sessionRepository.sumRevenueBySectorAndDate(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        RevenueDTO dto = revenueUseCase.getRevenue("A", DATE);

        assertThat(dto.getAmount()).isEqualByComparingTo("0.00");
    }
}
