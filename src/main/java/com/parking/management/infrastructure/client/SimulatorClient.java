package com.parking.management.infrastructure.client;

import com.parking.management.application.exception.ExternalServiceException;
import com.parking.management.domain.model.ControlTypeDTO;
import com.parking.management.domain.model.GateTypeDTO;
import com.parking.management.domain.model.SimulatorGarageDTO;
import com.parking.management.infrastructure.persistence.entity.GarageSectorEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingGateEntity;
import com.parking.management.infrastructure.persistence.entity.ParkingSpotEntity;
import com.parking.management.infrastructure.persistence.repository.GarageSectorRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingGateRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SimulatorClient implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulatorClient.class);

    private final RestTemplate restTemplate;
    private final GarageSectorRepository sectorRepository;
    private final ParkingSpotRepository spotRepository;
    private final ParkingGateRepository gateRepository;
    private final String simulatorBaseUrl;

    public SimulatorClient(RestTemplate restTemplate,
                           GarageSectorRepository sectorRepository,
                           ParkingSpotRepository spotRepository,
                           ParkingGateRepository gateRepository,
                           @Value("${simulator.base-url}") String simulatorBaseUrl) {
        this.restTemplate = restTemplate;
        this.sectorRepository = sectorRepository;
        this.spotRepository = spotRepository;
        this.gateRepository = gateRepository;
        this.simulatorBaseUrl = simulatorBaseUrl;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Fetching garage configuration from simulator at {}", simulatorBaseUrl);
        SimulatorGarageDTO response = fetchGarageConfig();
        persistSectors(response.getGarage());
        persistSpots(response.getSpots());
        persistGates(response.getGates());
        log.info("Garage initialized: {} sectors, {} spots, {} gates",
                response.getGarage() != null ? response.getGarage().size() : 0,
                response.getSpots() != null ? response.getSpots().size() : 0,
                response.getGates() != null ? response.getGates().size() : 0);
    }

    private SimulatorGarageDTO fetchGarageConfig() {
        String url = simulatorBaseUrl + "/garage";
        try {
            SimulatorGarageDTO response = restTemplate.getForObject(url, SimulatorGarageDTO.class);
            if (response == null) {
                throw new ExternalServiceException("Simulator returned null response from " + url);
            }
            return response;
        } catch (RestClientException e) {
            throw new ExternalServiceException(
                    "Failed to fetch garage configuration from simulator at " + url, e);
        }
    }

    public void persistSectors(List<SimulatorGarageDTO.GarageConfigDto> configList) {
        if (configList == null || configList.isEmpty()) {
            log.warn("No sectors returned from simulator; skipping sector persistence");
            return;
        }
        List<GarageSectorEntity> sectorList = new ArrayList<>();
        for (SimulatorGarageDTO.GarageConfigDto cfg : configList) {
            GarageSectorEntity sector = sectorRepository.findById(cfg.getSector())
                    .orElse(new GarageSectorEntity());
            sector.setSector(cfg.getSector());
            sector.setBasePrice(cfg.getBasePrice().setScale(2, RoundingMode.HALF_UP));
            sector.setMaxCapacity(cfg.getMaxCapacity());
            if (cfg.getControlType() != null) {
                try {
                    sector.setControlType(ControlTypeDTO.valueOf(cfg.getControlType().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown control_type '{}' for sector {}; defaulting to PHYSICAL",
                            cfg.getControlType(), cfg.getSector());
                    sector.setControlType(ControlTypeDTO.PHYSICAL);
                }
            } else {
                sector.setControlType(ControlTypeDTO.PHYSICAL);
            }
            sectorList.add(sector);
        }
        sectorRepository.saveAll(sectorList);
        log.debug("Persisted {} sectors", sectorList.size());
    }

    public void persistSpots(List<SimulatorGarageDTO.SpotConfigDto> configList) {
        if (configList == null || configList.isEmpty()) {
            log.warn("No spots returned from simulator; skipping spot persistence");
            return;
        }
        List<ParkingSpotEntity> spotList = new ArrayList<>();
        for (SimulatorGarageDTO.SpotConfigDto cfg : configList) {
            java.util.Optional<ParkingSpotEntity> existing = spotRepository.findById(cfg.getId());
            ParkingSpotEntity spot = existing.orElse(new ParkingSpotEntity());
            boolean isNew = existing.isEmpty();
            GarageSectorEntity sector = sectorRepository.getReferenceById(cfg.getSector());
            spot.setId(cfg.getId());
            spot.setSector(sector);
            spot.setLat(cfg.getLat());
            spot.setLng(cfg.getLng());
            if (isNew) {
                spot.setOccupied(false);
            }
            spotList.add(spot);
        }
        spotRepository.saveAll(spotList);
        log.debug("Persisted {} spots", spotList.size());
    }

    public void persistGates(List<SimulatorGarageDTO.GateConfigDto> configList) {
        if (configList == null || configList.isEmpty()) {
            log.warn("No gates returned from simulator; skipping gate persistence");
            return;
        }
        List<ParkingGateEntity> gateList = new ArrayList<>();
        for (SimulatorGarageDTO.GateConfigDto cfg : configList) {
            ParkingGateEntity gate = gateRepository.findById(cfg.getId()).orElse(new ParkingGateEntity());
            gate.setId(cfg.getId());
            GarageSectorEntity sector = sectorRepository.getReferenceById(cfg.getSector());
            gate.setSector(sector);
            try {
                gate.setGateType(GateTypeDTO.valueOf(cfg.getGateType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown gate type '{}' for gate {}; defaulting to BOTH",
                        cfg.getGateType(), cfg.getId());
                gate.setGateType(GateTypeDTO.BOTH);
            }
            gateList.add(gate);
        }
        gateRepository.saveAll(gateList);
        log.debug("Persisted {} gates", gateList.size());
    }
}
