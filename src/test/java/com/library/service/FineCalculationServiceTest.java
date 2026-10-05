package com.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FineCalculationServiceTest {

    private final FineCalculationService service = new FineCalculationService();

    @Test
    void calculatesFineAfterGracePeriodForReturnedQuantity() {
        var result = service.calculate(
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 13),
                2,
                BigDecimal.valueOf(5_000),
                1,
                null);

        assertEquals(2, result.lateDays());
        assertEquals(BigDecimal.valueOf(20_000), result.amount());
    }

    @Test
    void capsFineAtMaximumAmount() {
        var result = service.calculate(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 30),
                3,
                BigDecimal.valueOf(5_000),
                0,
                BigDecimal.valueOf(50_000));

        assertEquals(29, result.lateDays());
        assertEquals(BigDecimal.valueOf(50_000), result.amount());
    }

    @Test
    void returnsNoFineOnDueDate() {
        var result = service.calculate(
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 10),
                1,
                BigDecimal.valueOf(5_000),
                0,
                null);

        assertEquals(0, result.lateDays());
        assertEquals(BigDecimal.ZERO, result.amount());
    }
}