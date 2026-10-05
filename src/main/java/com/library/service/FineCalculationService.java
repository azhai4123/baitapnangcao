package com.library.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class FineCalculationService {

    public FineCalculation calculate(
            LocalDate dueDate,
            LocalDate actualReturnDate,
            int returnedQuantity,
            BigDecimal dailyFineAmount,
            int graceDays,
            BigDecimal maxFineAmount) {
        if (dueDate == null || actualReturnDate == null || dailyFineAmount == null
                || returnedQuantity < 0 || graceDays < 0 || dailyFineAmount.signum() < 0
                || (maxFineAmount != null && maxFineAmount.signum() < 0)) {
            throw new IllegalArgumentException("Thông tin tính phí phạt không hợp lệ.");
        }

        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(dueDate, actualReturnDate));
        int chargeableDays = Math.toIntExact(Math.max(0, overdueDays - graceDays));
        BigDecimal amount = dailyFineAmount
                .multiply(BigDecimal.valueOf(chargeableDays))
                .multiply(BigDecimal.valueOf(returnedQuantity));
        if (maxFineAmount != null) {
            amount = amount.min(maxFineAmount);
        }
        return new FineCalculation(chargeableDays, amount);
    }

    public record FineCalculation(int lateDays, BigDecimal amount) {
    }
}