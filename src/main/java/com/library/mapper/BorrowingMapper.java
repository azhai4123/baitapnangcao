package com.library.mapper;

import com.library.dto.BorrowingItemResponseDTO;
import com.library.dto.BorrowingResponseDTO;
import com.library.entity.Borrowing;
import com.library.entity.BorrowingStatus;
import java.time.LocalDate;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class BorrowingMapper {

    public BorrowingResponseDTO toResponse(Borrowing borrowing) {
        return toResponse(borrowing, LocalDate.now());
    }

    public BorrowingResponseDTO toResponse(Borrowing borrowing, LocalDate today) {
        boolean hasOutstandingCopies = borrowing.getDetails().stream()
                .anyMatch(detail -> !detail.isFullyReturned());
        String status = borrowing.getStatus() != BorrowingStatus.CANCELLED
                        && borrowing.getStatus() != BorrowingStatus.RETURNED
                        && hasOutstandingCopies
                        && borrowing.getDueDate().isBefore(today)
                ? BorrowingStatus.OVERDUE.name()
                : borrowing.getStatus().name();
        return new BorrowingResponseDTO(
                borrowing.getId(),
                borrowing.getMember().getId(),
                borrowing.getMember().getFullName(),
                borrowing.getBorrowedAt().toLocalDate(),
                borrowing.getDueDate(),
                status,
                borrowing.getFineStatus().name(),
                borrowing.getTotalFineAmount(),
                borrowing.getPaidFineAmount(),
                borrowing.getWaivedFineAmount(),
                borrowing.getUnpaidFineAmount(),
                borrowing.getNotes(),
                borrowing.getDetails().stream()
                        .sorted(Comparator.comparing(detail -> detail.getBook().getTitle()))
                        .map(detail -> new BorrowingItemResponseDTO(
                                detail.getId(),
                                detail.getBook().getId(),
                                detail.getBook().getTitle(),
                                detail.getQuantity(),
                                detail.getReturnedQuantity(),
                                detail.getReturnedAt(),
                                detail.getOverdueDays(),
                                detail.getFineAmount()))
                        .toList());
    }
}
