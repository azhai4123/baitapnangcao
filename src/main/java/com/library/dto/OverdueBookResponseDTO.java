package com.library.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OverdueBookResponseDTO(
        Long detailId,
        Long borrowingId,
        String memberName,
        String bookTitle,
        LocalDate dueDate,
        int overdueDays,
        int outstandingQuantity,
        BigDecimal estimatedFine) {
}
