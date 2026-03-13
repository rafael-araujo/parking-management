package com.parking.management.integration;

import com.parking.management.domain.model.ControlTypeDTO;
import com.parking.management.domain.model.GateTypeDTO;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingGateEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import com.parking.management.infrastructure.persistence.entity.VehicleSessionEntity;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingGateRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookIntegrationTest extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired GarageSectorRepository sectorRepository;
    @Autowired ParkingSpotRepository spotRepository;
    @Autowired ParkingGateRepository gateRepository;
    @Autowired VehicleSessionRepository sessionRepository;

    private GarageSectorEntity sector;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        gateRepository.deleteAll();
        spotRepository.deleteAll();
        sectorRepository.deleteAll();

        sector = sectorRepository.save(new GarageSectorEntity("A", new BigDecimal("10.00"), 2));

        ParkingSpotEntity spot1 = new ParkingSpotEntity();
        spot1.setId(1L);
        spot1.setSector(sector);
        spot1.setLat(new BigDecimal("-23.561684"));
        spot1.setLng(new BigDecimal("-46.655981"));
        spot1.setOccupied(false);

        ParkingSpotEntity spot2 = new ParkingSpotEntity();
        spot2.setId(2L);
        spot2.setSector(sector);
        spot2.setLat(new BigDecimal("-23.562000"));
        spot2.setLng(new BigDecimal("-46.656000"));
        spot2.setOccupied(false);

        spotRepository.saveAll(List.of(spot1, spot2));
    }

    @Test
    void entry_createsSession_andOccupiesSpot() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "entry_time": "2025-01-01T12:00:00.000Z",
                      "event_type": "ENTRY"
                    }
                    """))
                .andExpect(status().isNoContent());

        List<VehicleSessionEntity> sessions = sessionRepository.findAll();
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).getLicensePlate()).isEqualTo("ZUL0001");
        assertThat(sessions.get(0).getStatus()).isEqualTo(SessionStatusDTO.ENTERING);

        long occupied = spotRepository.countByOccupiedTrue();
        assertThat(occupied).isEqualTo(1);
    }

    @Test
    void entry_whenGarageFull_returns409() throws Exception {
        // Fill garage with active sessions (capacity = 2)
        List<ParkingSpotEntity> spots = spotRepository.findAll();
        saveEnteringSession("FULL001", LocalDateTime.parse("2025-01-01T10:00:00"), spots.get(0));
        saveEnteringSession("FULL002", LocalDateTime.parse("2025-01-01T10:05:00"), spots.get(1));

        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0002",
                      "entry_time": "2025-01-01T12:00:00.000Z",
                      "event_type": "ENTRY"
                    }
                    """))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateEntry_isIdempotent() throws Exception {
        String payload = """
            {
              "license_plate": "ZUL0001",
              "entry_time": "2025-01-01T12:00:00.000Z",
              "event_type": "ENTRY"
            }
            """;

        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findAll()).hasSize(1);
    }

    @Test
    void fullCycle_entry_parked_exit_billsCorrectly() throws Exception {
        // ENTRY
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "entry_time": "2025-01-01T12:00:00.000Z",
                      "event_type": "ENTRY"
                    }
                    """))
                .andExpect(status().isNoContent());

        // PARKED
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "lat": -23.561684,
                      "lng": -46.655981,
                      "event_type": "PARKED"
                    }
                    """))
                .andExpect(status().isNoContent());

        // EXIT after 60 minutes: occupancy at entry = 0/2 → multiplier 0.90
        // fee = 10.00 * 0.90 * 1h = 9.00
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "exit_time": "2025-01-01T13:00:00.000Z",
                      "event_type": "EXIT"
                    }
                    """))
                .andExpect(status().isNoContent());

        VehicleSessionEntity session = sessionRepository.findAll().stream()
                .filter(s -> "ZUL0001".equals(s.getLicensePlate()))
                .findFirst().orElseThrow();

        assertThat(session.getStatus()).isEqualTo(SessionStatusDTO.EXITED);
        assertThat(session.getAmountCharged()).isEqualByComparingTo("9.00");
        assertThat(spotRepository.countByOccupiedTrue()).isEqualTo(0L);
    }

    @Test
    void exitWithinFreeWindow_chargesZero() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0003",
                      "entry_time": "2025-01-01T12:00:00.000Z",
                      "event_type": "ENTRY"
                    }
                    """))
                .andExpect(status().isNoContent());

        // EXIT after 20 minutes — within free window (≤ 30 min)
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0003",
                      "exit_time": "2025-01-01T12:20:00.000Z",
                      "event_type": "EXIT"
                    }
                    """))
                .andExpect(status().isNoContent());

        VehicleSessionEntity session = sessionRepository.findAll().stream()
                .filter(s -> "ZUL0003".equals(s.getLicensePlate()))
                .findFirst().orElseThrow();

        assertThat(session.getAmountCharged()).isEqualByComparingTo("0.00");
    }

    @Test
    void entry_missingEntryTime_returns400() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "event_type": "ENTRY"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exit_missingExitTime_returns400() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "event_type": "EXIT"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEventType_returns400() throws Exception {
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "ZUL0001",
                      "event_type": "UNKNOWN"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }

    // --- LOGICAL sector tests ---

    @Test
    void logicalSector_fullCycle_entry_parked_exit_billsCorrectly() throws Exception {
        GarageSectorEntity logicalSector = new GarageSectorEntity("B", new BigDecimal("8.00"), 10);
        logicalSector.setControlType(ControlTypeDTO.LOGICAL);
        sectorRepository.save(logicalSector);

        ParkingGateEntity gate = new ParkingGateEntity();
        gate.setId("GATE-B1");
        gate.setSector(logicalSector);
        gate.setGateType(GateTypeDTO.ENTRY);
        gateRepository.save(gate);

        // ENTRY via gate linked to LOGICAL sector
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "LOG0001",
                      "entry_time": "2025-02-01T10:00:00.000Z",
                      "event_type": "ENTRY",
                      "gate_id": "GATE-B1"
                    }
                    """))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findAll())
                .filteredOn(s -> "LOG0001".equals(s.getLicensePlate()))
                .hasSize(1)
                .first()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatusDTO.ENTERING));

        // PARKED — no lat/lng required for LOGICAL
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "LOG0001",
                      "event_type": "PARKED"
                    }
                    """))
                .andExpect(status().isNoContent());

        assertThat(sessionRepository.findAll())
                .filteredOn(s -> "LOG0001".equals(s.getLicensePlate()))
                .first()
                .satisfies(s -> assertThat(s.getStatus()).isEqualTo(SessionStatusDTO.PARKED));

        // EXIT after 2 hours: fee = 8.00 * multiplier * 2h
        // occupancy 0/10 → multiplier 0.90 → fee = 8.00 * 0.90 * 2 = 14.40
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "LOG0001",
                      "exit_time": "2025-02-01T12:00:00.000Z",
                      "event_type": "EXIT"
                    }
                    """))
                .andExpect(status().isNoContent());

        VehicleSessionEntity session = sessionRepository.findAll().stream()
                .filter(s -> "LOG0001".equals(s.getLicensePlate()))
                .findFirst().orElseThrow();

        assertThat(session.getStatus()).isEqualTo(SessionStatusDTO.EXITED);
        assertThat(session.getAmountCharged()).isEqualByComparingTo("14.40");
        // LOGICAL sectors do not use physical spots — occupied count must remain 0
        assertThat(spotRepository.countByOccupiedTrue()).isEqualTo(0L);
    }

    @Test
    void logicalSector_entry_whenSectorFull_returns409() throws Exception {
        GarageSectorEntity logicalSector = new GarageSectorEntity("C", new BigDecimal("5.00"), 1);
        logicalSector.setControlType(ControlTypeDTO.LOGICAL);
        sectorRepository.save(logicalSector);

        ParkingGateEntity gate = new ParkingGateEntity();
        gate.setId("GATE-C1");
        gate.setSector(logicalSector);
        gate.setGateType(GateTypeDTO.ENTRY);
        gateRepository.save(gate);

        // Fill the sector (capacity = 1)
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "LOG0010",
                      "entry_time": "2025-02-01T09:00:00.000Z",
                      "event_type": "ENTRY",
                      "gate_id": "GATE-C1"
                    }
                    """))
                .andExpect(status().isNoContent());

        // Second vehicle should be rejected with 409
        mockMvc.perform(post("/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "license_plate": "LOG0011",
                      "entry_time": "2025-02-01T09:05:00.000Z",
                      "event_type": "ENTRY",
                      "gate_id": "GATE-C1"
                    }
                    """))
                .andExpect(status().isConflict());
    }

    // --- Concurrency test ---

    @Test
    void concurrentEntry_pessimisticLock_preventsDoubleReservation() throws Exception {
        // Garage has exactly 2 spots; 4 threads race to enter simultaneously
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String plate = String.format("CONC%04d", i);
            futures.add(executor.submit(() -> {
                startGate.await();
                MvcResult result = mockMvc.perform(post("/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "license_plate": "%s",
                              "entry_time": "2025-06-01T10:00:00.000Z",
                              "event_type": "ENTRY"
                            }
                            """.formatted(plate)))
                        .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        startGate.countDown(); // release all threads at once
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        List<Integer> statuses = futures.stream()
                .map(f -> { try { return f.get(); } catch (Exception e) { return -1; } })
                .toList();

        long successCount  = statuses.stream().filter(s -> s == 204).count();
        long rejectedCount = statuses.stream().filter(s -> s == 409).count();

        // Only 2 entries must succeed — exactly as many as available spots
        assertThat(successCount).isEqualTo(2);
        assertThat(rejectedCount).isEqualTo(2);
        assertThat(sessionRepository.findAll()).hasSize(2);
        assertThat(spotRepository.countByOccupiedTrue()).isEqualTo(2);
    }

    private void saveEnteringSession(String plate, LocalDateTime entryTime, ParkingSpotEntity spot) {
        spot.setOccupied(true);
        spotRepository.save(spot);

        VehicleSessionEntity session = new VehicleSessionEntity();
        session.setLicensePlate(plate);
        session.setEntryTime(entryTime);
        session.setSpot(spot);
        session.setSector(sector);
        session.setPriceMultiplier(new BigDecimal("1.00"));
        session.setStatus(SessionStatusDTO.ENTERING);
        sessionRepository.save(session);
    }
}
