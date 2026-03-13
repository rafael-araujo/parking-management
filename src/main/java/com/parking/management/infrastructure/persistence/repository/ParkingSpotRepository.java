package com.parking.management.infrastructure.persistence.repository;

import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpotEntity, Long> {

    long countByOccupiedTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSpotEntity> findFirstByOccupiedFalseOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ParkingSpotEntity> findFirstBySectorAndOccupiedFalseOrderByIdAsc(GarageSectorEntity sector);

    @Query(value = "SELECT * FROM parking_spot ORDER BY (POW(lat - :lat, 2) + POW(lng - :lng, 2)) ASC LIMIT 1",
           nativeQuery = true)
    Optional<ParkingSpotEntity> findNearestToCoordinates(@Param("lat") BigDecimal lat,
                                                         @Param("lng") BigDecimal lng);
}
