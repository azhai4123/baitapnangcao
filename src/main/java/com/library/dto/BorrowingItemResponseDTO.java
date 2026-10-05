package com.library.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BorrowingItemResponseDTO(
        Long id,
        Long bookId,
        String title,
        int quantity,
        int returnedQuantity,
        LocalDateTime returnedAt,
        int overdueDays,
        BigDecimal fineAmount) {
}
