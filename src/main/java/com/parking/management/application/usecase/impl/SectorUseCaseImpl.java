package com.parking.management.application.usecase.impl;

import com.parking.management.application.exception.ConflictException;
import com.parking.management.application.usecase.SectorUseCase;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SectorUseCaseImpl implements SectorUseCase {

    private static final List<SessionStatusDTO> ACTIVE_STATUS_LIST =
            List.of(SessionStatusDTO.ENTERING, SessionStatusDTO.PARKED);

    private final ParkingSpotRepository spotRepository;
    private final GarageSectorRepository sectorRepository;
    private final VehicleSessionRepository sessionRepository;

    public SectorUseCaseImpl(ParkingSpotRepository spotRepository,
                             GarageSectorRepository sectorRepository,
                             VehicleSessionRepository sessionRepository) {
        this.spotRepository = spotRepository;
        this.sectorRepository = sectorRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGarageFull() {
        long activeSessions = sessionRepository.countByStatusIn(ACTIVE_STATUS_LIST);
        long total = sectorRepository.sumTotalCapacity();
        return total > 0 && activeSessions >= total;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSectorFull(GarageSectorEntity sector) {
        long activeSessions = sessionRepository.countBySectorAndStatusIn(sector, ACTIVE_STATUS_LIST);
        return sector.getMaxCapacity() > 0 && activeSessions >= sector.getMaxCapacity();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getOccupancyRatio() {
        long activeSessions = sessionRepository.countByStatusIn(ACTIVE_STATUS_LIST);
        long total = sectorRepository.sumTotalCapacity();
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(activeSessions)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public ParkingSpotEntity reserveAvailableSpot() {
        ParkingSpotEntity spot = spotRepository.findFirstByOccupiedFalseOrderByIdAsc()
                .orElseThrow(() -> new ConflictException("Garage is full"));
        spot.setOccupied(true);
        return spotRepository.save(spot);
    }

    @Override
    @Transactional
    public ParkingSpotEntity reserveAvailableSpotInSector(GarageSectorEntity sector) {
        ParkingSpotEntity spot = spotRepository.findFirstBySectorAndOccupiedFalseOrderByIdAsc(sector)
                .orElseThrow(() -> new ConflictException("Sector is full"));
        spot.setOccupied(true);
        return spotRepository.save(spot);
    }
}
