package com.parking.management.domain.service;

import com.parking.management.domain.exception.BusinessException;
import com.parking.management.domain.service.impl.BillingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingServiceTest {

    private BillingService billingService;

    private static final BigDecimal BASE = new BigDecimal("10.00");
    private static final BigDecimal MULT_NORMAL = new BigDecimal("1.00");
    private static final LocalDateTime ENTRY = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

    @BeforeEach
    void setUp() {
        billingService = new BillingServiceImpl();
    }

    @Test
    void calculate_20min_isFree() {
        LocalDateTime exit = ENTRY.plusMinutes(20);
        assertThat(billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void calculate_exactlyAt30min_isFree() {
        LocalDateTime exit = ENTRY.plusMinutes(30);
        assertThat(billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void calculate_31min_chargesOneHour() {
        LocalDateTime exit = ENTRY.plusMinutes(31);
        assertThat(billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void calculate_60min_chargesOneHour() {
        LocalDateTime exit = ENTRY.plusMinutes(60);
        assertThat(billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void calculate_61min_chargesTwoHours() {
        LocalDateTime exit = ENTRY.plusMinutes(61);
        assertThat(billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void calculate_90min_chargesTwoHours() {
        LocalDateTime exit = ENTRY.plusMinutes(90);
        assertThat(billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void calculate_withDiscount_090() {
        LocalDateTime exit = ENTRY.plusMinutes(60);
        BigDecimal mult = new BigDecimal("0.90");
        assertThat(billingService.calculate(ENTRY, exit, BASE, mult))
                .isEqualByComparingTo("9.00");
    }

    @Test
    void calculate_withSurcharge_110() {
        LocalDateTime exit = ENTRY.plusMinutes(60);
        BigDecimal mult = new BigDecimal("1.10");
        assertThat(billingService.calculate(ENTRY, exit, BASE, mult))
                .isEqualByComparingTo("11.00");
    }

    @Test
    void calculate_withPeakSurcharge_125() {
        LocalDateTime exit = ENTRY.plusMinutes(60);
        BigDecimal mult = new BigDecimal("1.25");
        assertThat(billingService.calculate(ENTRY, exit, BASE, mult))
                .isEqualByComparingTo("12.50");
    }

    @Test
    void calculate_multiHour_withPeakSurcharge() {
        LocalDateTime exit = ENTRY.plusMinutes(90);
        BigDecimal mult = new BigDecimal("1.25");
        assertThat(billingService.calculate(ENTRY, exit, BASE, mult))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void calculate_exitBeforeEntry_throwsBusinessException() {
        LocalDateTime exit = ENTRY.minusMinutes(10);
        assertThatThrownBy(() -> billingService.calculate(ENTRY, exit, BASE, MULT_NORMAL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exitTime cannot be before entryTime");
    }
}
