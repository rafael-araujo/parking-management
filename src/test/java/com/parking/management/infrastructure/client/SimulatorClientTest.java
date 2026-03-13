package com.parking.management.infrastructure.client;

import com.parking.management.application.exception.ExternalServiceException;
import com.parking.management.domain.model.SimulatorGarageDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingGateRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulatorClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private GarageSectorRepository sectorRepository;
    @Mock private ParkingSpotRepository spotRepository;
    @Mock private ParkingGateRepository gateRepository;

    private SimulatorClient simulatorClient;

    @BeforeEach
    void setUp() {
        simulatorClient = new SimulatorClient(
                restTemplate, sectorRepository, spotRepository, gateRepository, "http://localhost:3000");
    }

    @Test
    void persistSectors_savesAllSectors() {
        SimulatorGarageDTO.GarageConfigDto cfg = new SimulatorGarageDTO.GarageConfigDto();
        cfg.setSector("A");
        cfg.setBasePrice(new BigDecimal("10.00"));
        cfg.setMaxCapacity(100);

        when(sectorRepository.findById("A")).thenReturn(Optional.empty());
        when(sectorRepository.saveAll(any())).thenReturn(List.of());

        simulatorClient.persistSectors(List.of(cfg));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GarageSectorEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(sectorRepository).saveAll(captor.capture());
        List<GarageSectorEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSector()).isEqualTo("A");
        assertThat(saved.get(0).getBasePrice()).isEqualByComparingTo("10.00");
        assertThat(saved.get(0).getMaxCapacity()).isEqualTo(100);
    }

    @Test
    void run_throwsWhenSimulatorUnavailable() {
        when(restTemplate.getForObject(anyString(), eq(SimulatorGarageDTO.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> simulatorClient.run(null))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Failed to fetch garage configuration");
    }
}
