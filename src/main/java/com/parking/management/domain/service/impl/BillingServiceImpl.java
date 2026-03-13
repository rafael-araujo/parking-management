package com.parking.management.domain.service.impl;

import com.parking.management.domain.exception.BusinessException;
import com.parking.management.domain.service.BillingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class BillingServiceImpl implements BillingService {

    private static final long FREE_PERIOD_MINUTES = 30;

    /**
     * Calculates the parking fee.
     * <ul>
     *   <li>Stays of 30 minutes or less are free (R$0.00).</li>
     *   <li>Stays over 30 minutes are charged per hour, ceiling applied:
     *       {@code fee = basePrice × multiplier × ceil(minutes / 60)}</li>
     * </ul>
     */
    @Override
    public BigDecimal calculate(LocalDateTime entryTime, LocalDateTime exitTime,
                                BigDecimal basePrice, BigDecimal multiplier) {
        long durationMinutes = ChronoUnit.MINUTES.between(entryTime, exitTime);

        if (durationMinutes < 0) {
            throw new BusinessException(
                    "exitTime cannot be before entryTime: entry=" + entryTime + ", exit=" + exitTime);
        }

        if (durationMinutes <= FREE_PERIOD_MINUTES) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        long billableHours = (long) Math.ceil(durationMinutes / 60.0);
        return basePrice
                .multiply(multiplier)
                .multiply(BigDecimal.valueOf(billableHours))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
