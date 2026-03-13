package com.parking.management.infrastructure.persistence.repository;

import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.VehicleSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VehicleSessionRepository extends JpaRepository<VehicleSessionEntity, Long> {

    List<VehicleSessionEntity> findByLicensePlateAndStatusIn(String licensePlate, List<SessionStatusDTO> statuses);

    long countByStatusIn(List<SessionStatusDTO> statuses);

    long countBySectorAndStatusIn(GarageSectorEntity sector, List<SessionStatusDTO> statuses);

    @Query("SELECT COALESCE(SUM(v.amountCharged), 0) FROM VehicleSession v " +
           "WHERE v.sector.sector = :sector " +
           "AND v.exitTime >= :startOfDay " +
           "AND v.exitTime < :startOfNextDay " +
           "AND v.status = :status")
    BigDecimal sumRevenueBySectorAndDate(@Param("sector") String sector,
                                         @Param("startOfDay") LocalDateTime startOfDay,
                                         @Param("startOfNextDay") LocalDateTime startOfNextDay,
                                         @Param("status") SessionStatusDTO status);
}
