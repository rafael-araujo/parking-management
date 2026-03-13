package com.parking.management.infrastructure.persistence.repository;

import com.parking.management.infrastructure.persistence.entity.ParkingGateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingGateRepository extends JpaRepository<ParkingGateEntity, String> {
}
