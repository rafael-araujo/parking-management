package com.parking.management.domain.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface BillingService {

    BigDecimal calculate(LocalDateTime entryTime, LocalDateTime exitTime,
                         BigDecimal basePrice, BigDecimal multiplier);
}
