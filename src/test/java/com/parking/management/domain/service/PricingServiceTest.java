package com.parking.management.domain.service;

import com.parking.management.domain.service.impl.PricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingServiceImpl();
    }

    @ParameterizedTest(name = "ratio={0} -> multiplier={1}")
    @CsvSource({
        "0.00, 0.90",
        "0.10, 0.90",
        "0.24, 0.90",
        "0.25, 1.00",
        "0.40, 1.00",
        "0.49, 1.00",
        "0.50, 1.10",
        "0.60, 1.10",
        "0.74, 1.10",
        "0.75, 1.25",
        "0.90, 1.25",
        "0.99, 1.25"
    })
    void getMultiplier_returnsCorrectTierForRatio(String ratioStr, String expectedStr) {
        BigDecimal ratio = new BigDecimal(ratioStr);
        BigDecimal expected = new BigDecimal(expectedStr);
        assertThat(pricingService.getMultiplier(ratio)).isEqualByComparingTo(expected);
    }

}
