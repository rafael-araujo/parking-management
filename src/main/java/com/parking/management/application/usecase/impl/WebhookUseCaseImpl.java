package com.parking.management.application.usecase.impl;

import com.parking.management.application.exception.ConflictException;
import com.parking.management.application.exception.InvalidEventException;
import com.parking.management.application.exception.ResourceNotFoundException;
import com.parking.management.application.usecase.SectorUseCase;
import com.parking.management.application.usecase.WebhookUseCase;
import com.parking.management.domain.model.ControlTypeDTO;
import com.parking.management.domain.model.SessionStatusDTO;
import com.parking.management.domain.service.BillingService;
import com.parking.management.domain.service.PricingService;
import com.parking.management.infrastructure.persistence.entity.*;
import com.parking.management.infrastructure.persistence.repository.ParkingGateRepository;
import com.parking.management.infrastructure.persistence.repository.ParkingSpotRepository;
import com.parking.management.infrastructure.persistence.repository.VehicleSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WebhookUseCaseImpl implements WebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(WebhookUseCaseImpl.class);

    private final SectorUseCase sectorUseCase;
    private final PricingService pricingService;
    private final BillingService billingService;
    private final VehicleSessionRepository sessionRepository;
    private final ParkingSpotRepository spotRepository;
    private final ParkingGateRepository gateRepository;

    public WebhookUseCaseImpl(SectorUseCase sectorUseCase,
                              PricingService pricingService,
                              BillingService billingService,
                              VehicleSessionRepository sessionRepository,
                              ParkingSpotRepository spotRepository,
                              ParkingGateRepository gateRepository) {
        this.sectorUseCase = sectorUseCase;
        this.pricingService = pricingService;
        this.billingService = billingService;
        this.sessionRepository = sessionRepository;
        this.spotRepository = spotRepository;
        this.gateRepository = gateRepository;
    }

    @Override
    @Transactional
    public void processEntry(String licensePlate, LocalDateTime entryTime, String gateId) {
        if (entryTime == null) {
            throw new InvalidEventException("entry_time is required for ENTRY events");
        }

        List<VehicleSessionEntity> activeList = sessionRepository.findByLicensePlateAndStatusIn(
                licensePlate, List.of(SessionStatusDTO.ENTERING, SessionStatusDTO.PARKED));
        if (!activeList.isEmpty()) {
            log.warn("Duplicate ENTRY ignored for plate={}", licensePlate);
            return;
        }

        ParkingGateEntity gate = resolveGate(gateId);
        GarageSectorEntity sector = gate != null ? gate.getSector() : null;

        if (sector != null && sector.getControlType() == ControlTypeDTO.LOGICAL) {
            processLogicalEntry(licensePlate, entryTime, gate, sector);
        } else {
            processPhysicalEntry(licensePlate, entryTime, gate, sector);
        }
    }

    private void processPhysicalEntry(String licensePlate, LocalDateTime entryTime,
                                      ParkingGateEntity gate, GarageSectorEntity sector) {
        if (sectorUseCase.isGarageFull()) {
            throw new ConflictException("Garage is full");
        }

        BigDecimal occupancyRatio = sectorUseCase.getOccupancyRatio();
        BigDecimal multiplier = pricingService.getMultiplier(occupancyRatio);

        ParkingSpotEntity spot = (sector != null)
                ? sectorUseCase.reserveAvailableSpotInSector(sector)
                : sectorUseCase.reserveAvailableSpot();

        VehicleSessionEntity session = new VehicleSessionEntity();
        session.setLicensePlate(licensePlate);
        session.setEntryTime(entryTime);
        session.setSpot(spot);
        session.setSector(spot.getSector());
        session.setGate(gate);
        session.setPriceMultiplier(multiplier);
        session.setStatus(SessionStatusDTO.ENTERING);
        sessionRepository.save(session);

        log.info("ENTRY (PHYSICAL) processed: plate={}, multiplier={}, spotId={}, gate={}",
                licensePlate, multiplier, spot.getId(), gate != null ? gate.getId() : "none");
    }

    private void processLogicalEntry(String licensePlate, LocalDateTime entryTime,
                                     ParkingGateEntity gate, GarageSectorEntity sector) {
        // gate is guaranteed non-null here: sector != null implies gate != null (see resolveGate + caller)
        if (sectorUseCase.isSectorFull(sector)) {
            throw new ConflictException("Sector is full");
        }

        BigDecimal occupancyRatio = sectorUseCase.getOccupancyRatio();
        BigDecimal multiplier = pricingService.getMultiplier(occupancyRatio);

        VehicleSessionEntity session = new VehicleSessionEntity();
        session.setLicensePlate(licensePlate);
        session.setEntryTime(entryTime);
        session.setSector(sector);
        session.setGate(gate);
        session.setPriceMultiplier(multiplier);
        session.setStatus(SessionStatusDTO.ENTERING);
        sessionRepository.save(session);

        log.info("ENTRY (LOGICAL) processed: plate={}, multiplier={}, sector={}, gate={}",
                licensePlate, multiplier, sector.getSector(), gate.getId());
    }

    @Override
    @Transactional
    public void processParked(String licensePlate, BigDecimal lat, BigDecimal lng) {
        Optional<VehicleSessionEntity> sessionOpt = findActiveSession(licensePlate);
        if (sessionOpt.isEmpty()) {
            throw new ResourceNotFoundException("No active session found for plate: " + licensePlate);
        }

        VehicleSessionEntity session = sessionOpt.get();

        if (session.getSector().getControlType() == ControlTypeDTO.LOGICAL) {
            session.setStatus(SessionStatusDTO.PARKED);
            sessionRepository.save(session);
            log.info("PARKED (LOGICAL) accepted: plate={}, sector={}", licensePlate,
                    session.getSector().getSector());
            return;
        }

        if (lat == null || lng == null) {
            throw new InvalidEventException("lat and lng are required for PARKED events");
        }

        Optional<ParkingSpotEntity> nearestSpot = spotRepository.findNearestToCoordinates(lat, lng);
        if (nearestSpot.isEmpty()) {
            throw new InvalidEventException(
                    "No parking spot found near coordinates lat=" + lat + ", lng=" + lng);
        }

        ParkingSpotEntity spot = nearestSpot.get();

        if (!spot.getId().equals(session.getSpot() != null ? session.getSpot().getId() : null)) {
            if (session.getSpot() != null) {
                session.getSpot().setOccupied(false);
                spotRepository.save(session.getSpot());
            }
            spot.setOccupied(true);
            spotRepository.save(spot);
        }

        session.setSpot(spot);
        session.setSector(spot.getSector());
        session.setStatus(SessionStatusDTO.PARKED);
        sessionRepository.save(session);

        log.info("PARKED (PHYSICAL) processed: plate={}, spotId={}", licensePlate, spot.getId());
    }

    @Override
    @Transactional
    public void processExit(String licensePlate, LocalDateTime exitTime) {
        if (exitTime == null) {
            throw new InvalidEventException("exit_time is required for EXIT events");
        }

        Optional<VehicleSessionEntity> sessionOpt = findActiveSession(licensePlate);
        if (sessionOpt.isEmpty()) {
            throw new ResourceNotFoundException("No active session found for plate: " + licensePlate);
        }

        VehicleSessionEntity session = sessionOpt.get();
        // EXIT is accepted from both ENTERING and PARKED states to handle quick pass-throughs
        // where the vehicle leaves before sending a PARKED event.
        BigDecimal basePrice = session.getSector().getBasePrice();
        BigDecimal amount = billingService.calculate(
                session.getEntryTime(), exitTime, basePrice, session.getPriceMultiplier());

        session.setExitTime(exitTime);
        session.setAmountCharged(amount);
        session.setStatus(SessionStatusDTO.EXITED);

        if (session.getSector().getControlType() == ControlTypeDTO.PHYSICAL
                && session.getSpot() != null) {
            session.getSpot().setOccupied(false);
            spotRepository.save(session.getSpot());
        }

        sessionRepository.save(session);

        log.info("EXIT processed: plate={}, entryTime={}, exitTime={}, amount={}, controlType={}",
                licensePlate, session.getEntryTime(), exitTime, amount,
                session.getSector().getControlType());
    }

    private ParkingGateEntity resolveGate(String gateId) {
        if (gateId == null || gateId.isBlank()) {
            return null;
        }
        ParkingGateEntity gate = gateRepository.findById(gateId).orElse(null);
        if (gate == null) {
            log.warn("ENTRY event received with unknown gate_id='{}'; proceeding without gate/sector context", gateId);
        }
        return gate;
    }

    private Optional<VehicleSessionEntity> findActiveSession(String licensePlate) {
        List<VehicleSessionEntity> sessionList = sessionRepository.findByLicensePlateAndStatusIn(
                licensePlate, List.of(SessionStatusDTO.ENTERING, SessionStatusDTO.PARKED));
        return sessionList.stream().findFirst();
    }
}
