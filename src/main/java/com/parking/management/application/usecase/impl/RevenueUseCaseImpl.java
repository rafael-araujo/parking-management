package com.parking.management.application.usecase.impl;

import com.parking.management.application.exception.ResourceNotFoundException;
import com.parking.management.application.usecase.RevenueUseCase;
import com.parking.management.domain.model.RevenueDTO;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class RevenueUseCaseImpl implements RevenueUseCase {

    private final VehicleSessionRepository sessionRepository;
    private final GarageSectorRepository sectorRepository;

    public RevenueUseCaseImpl(VehicleSessionRepository sessionRepository,
                              GarageSectorRepository sectorRepository) {
        this.sessionRepository = sessionRepository;
        this.sectorRepository = sectorRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueDTO getRevenue(String sector, LocalDate date) {
        if (!sectorRepository.existsById(sector)) {
            throw new ResourceNotFoundException("Sector '" + sector + "' not found");
        }

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime startOfNextDay = date.plusDays(1).atStartOfDay();
        BigDecimal total = sessionRepository.sumRevenueBySectorAndDate(
                sector, startOfDay, startOfNextDay, SessionStatusDTO.EXITED);

        return new RevenueDTO(
                total.setScale(2, RoundingMode.HALF_UP),
                "BRL",
                Instant.now());
    }
}
