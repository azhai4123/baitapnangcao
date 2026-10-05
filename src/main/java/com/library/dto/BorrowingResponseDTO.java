package com.library.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public record BorrowingResponseDTO(
        Long id,
        Long memberId,
        String memberName,
        LocalDate borrowedDate,
        LocalDate dueDate,
        String status,
        String fineStatus,
        BigDecimal totalFineAmount,
        BigDecimal paidFineAmount,
        BigDecimal waivedFineAmount,
        BigDecimal unpaidFineAmount,
        String notes,
        List<BorrowingItemResponseDTO> items) {
}
