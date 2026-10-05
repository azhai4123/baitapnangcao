package com.library.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReturnPreviewResponseDTO(
        Long borrowingId,
        LocalDate returnDate,
        List<ReturnLineResponseDTO> lines,
        BigDecimal totalFine) {
}
