package com.parking.management.integration;

import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import com.parking.management.infrastructure.persistence.entity.VehicleSessionEntity;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RevenueIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired GarageSectorRepository sectorRepository;
    @Autowired ParkingSpotRepository spotRepository;
    @Autowired VehicleSessionRepository sessionRepository;

    private GarageSectorEntity sectorA;
    private ParkingSpotEntity spot1;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        spotRepository.deleteAll();
        sectorRepository.deleteAll();

        sectorA = sectorRepository.save(new GarageSectorEntity("A", new BigDecimal("10.00"), 10));

        spot1 = new ParkingSpotEntity();
        spot1.setId(10L);
        spot1.setSector(sectorA);
        spot1.setLat(new BigDecimal("-23.561684"));
        spot1.setLng(new BigDecimal("-46.655981"));
        spot1.setOccupied(false);
        spot1 = spotRepository.save(spot1);
    }

    @Test
    void getRevenue_returnsSumOfCompletedSessions() throws Exception {
        saveExitedSession("AAA0001", "2025-01-01T12:00:00", "2025-01-01T13:00:00",
                new BigDecimal("10.00"));
        saveExitedSession("AAA0002", "2025-01-01T14:00:00", "2025-01-01T16:00:00",
                new BigDecimal("20.00"));

        mockMvc.perform(get("/revenue")
                .param("date", "2025-01-01")
                .param("sector", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("30.00"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void getRevenue_doesNotIncludeSessionsFromOtherDays() throws Exception {
        saveExitedSession("AAA0004", "2025-01-01T12:00:00", "2025-01-01T13:00:00",
                new BigDecimal("10.00"));
        saveExitedSession("AAA0005", "2025-01-02T12:00:00", "2025-01-02T13:00:00",
                new BigDecimal("99.00"));

        mockMvc.perform(get("/revenue")
                .param("date", "2025-01-01")
                .param("sector", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("10.00"));
    }

    @Test
    void getRevenue_returnsZeroWhenNoSessions() throws Exception {
        mockMvc.perform(get("/revenue")
                .param("date", "2025-01-01")
                .param("sector", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("0.00"));
    }

    @Test
    void getRevenue_returns404ForUnknownSector() throws Exception {
        mockMvc.perform(get("/revenue")
                .param("date", "2025-01-01")
                .param("sector", "Z"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRevenue_returns400ForMissingDate() throws Exception {
        mockMvc.perform(get("/revenue")
                .param("sector", "A"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRevenue_returns400ForMissingSector() throws Exception {
        mockMvc.perform(get("/revenue")
                .param("date", "2025-01-01"))
                .andExpect(status().isBadRequest());
    }

    private void saveExitedSession(String plate, String entryStr, String exitStr,
                                   BigDecimal amount) {
        VehicleSessionEntity session = new VehicleSessionEntity();
        session.setLicensePlate(plate);
        session.setEntryTime(LocalDateTime.parse(entryStr));
        session.setExitTime(LocalDateTime.parse(exitStr));
        session.setSpot(spot1);
        session.setSector(sectorA);
        session.setPriceMultiplier(new BigDecimal("1.00"));
        session.setAmountCharged(amount);
        session.setStatus(SessionStatusDTO.EXITED);
        sessionRepository.save(session);
    }
}
