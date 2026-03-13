package com.parking.management.domain.service.impl;

import com.parking.management.domain.service.PricingService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PricingServiceImpl implements PricingService {

    private static final BigDecimal DISCOUNT    = new BigDecimal("0.90");
    private static final BigDecimal NORMAL      = new BigDecimal("1.00");
    private static final BigDecimal SURCHARGE   = new BigDecimal("1.10");
    private static final BigDecimal PEAK        = new BigDecimal("1.25");

    private static final BigDecimal TIER_LOW    = new BigDecimal("0.25");
    private static final BigDecimal TIER_MID    = new BigDecimal("0.50");
    private static final BigDecimal TIER_HIGH   = new BigDecimal("0.75");

    /**
     * Returns the price multiplier based on current garage occupancy ratio.
     * <pre>
     *  ratio < 0.25  -> 0.90 (10% discount)
     *  ratio < 0.50  -> 1.00 (no change)
     *  ratio < 0.75  -> 1.10 (10% surcharge)
     *  ratio >= 0.75 -> 1.25 (25% surcharge)
     * </pre>
     */
    @Override
    public BigDecimal getMultiplier(BigDecimal occupancyRatio) {
        if (occupancyRatio.compareTo(TIER_LOW) < 0) {
            return DISCOUNT;
        } else if (occupancyRatio.compareTo(TIER_MID) < 0) {
            return NORMAL;
        } else if (occupancyRatio.compareTo(TIER_HIGH) < 0) {
            return SURCHARGE;
        } else {
            return PEAK;
        }
    }
}
