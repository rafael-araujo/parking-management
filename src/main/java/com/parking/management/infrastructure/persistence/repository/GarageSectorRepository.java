package com.parking.management.infrastructure.persistence.repository;

import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GarageSectorRepository extends JpaRepository<GarageSectorEntity, String> {

    @Query("SELECT COALESCE(SUM(g.maxCapacity), 0) FROM GarageSector g")
    long sumTotalCapacity();
}
