package com.parking.management.domain.service;

import java.math.BigDecimal;

public interface PricingService {

    BigDecimal getMultiplier(BigDecimal occupancyRatio);
}
