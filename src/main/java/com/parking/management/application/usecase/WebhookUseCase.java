package com.parking.management.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface WebhookUseCase {

    void processEntry(String licensePlate, LocalDateTime entryTime, String gateId);

    void processParked(String licensePlate, BigDecimal lat, BigDecimal lng);

    void processExit(String licensePlate, LocalDateTime exitTime);
}
