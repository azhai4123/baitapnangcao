package com.library.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReturnLineResponseDTO(
        Long detailId,
        String bookTitle,
        int returnedQuantity,
        LocalDate dueDate,
        int overdueDays,
        BigDecimal fineAmount) {
}
