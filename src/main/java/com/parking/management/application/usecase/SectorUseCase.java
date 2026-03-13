package com.parking.management.application.usecase;

import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;

import java.math.BigDecimal;

public interface SectorUseCase {

    boolean isGarageFull();

    boolean isSectorFull(GarageSectorEntity sector);

    BigDecimal getOccupancyRatio();

    ParkingSpotEntity reserveAvailableSpot();

    ParkingSpotEntity reserveAvailableSpotInSector(GarageSectorEntity sector);
}
