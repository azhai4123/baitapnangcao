package com.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "borrowing_details")
@Getter
@NoArgsConstructor
public class BorrowingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrowing_id", nullable = false)
    private Borrowing borrowing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "returned_quantity", nullable = false)
    private int returnedQuantity;

    @Column(name = "last_returned_date")
    private LocalDateTime lastReturnedDate;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "late_days", nullable = false)
    private int overdueDays;

    @Column(name = "fine_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private int version;

    public BorrowingDetail(Borrowing borrowing, Book book) {
        this(borrowing, book, 1);
    }

    public BorrowingDetail(Borrowing borrowing, Book book, int quantity) {
        this.borrowing = borrowing;
        this.book = book;
        this.quantity = quantity;
    }

    public void markReturned(int quantity, LocalDateTime returnedAt, int lateDays, BigDecimal fineAmount) {
        if (quantity < 1 || quantity > this.quantity - returnedQuantity) {
            throw new IllegalStateException("Số lượng trả vượt quá số sách đang mượn.");
        }
        returnedQuantity += quantity;
        lastReturnedDate = returnedAt;
        overdueDays = Math.max(overdueDays, lateDays);
        this.fineAmount = this.fineAmount.add(fineAmount);
        if (isFullyReturned()) {
            this.returnedAt = returnedAt;
        }
    }

    public boolean isFullyReturned() {
        return returnedQuantity >= quantity;
    }
}
