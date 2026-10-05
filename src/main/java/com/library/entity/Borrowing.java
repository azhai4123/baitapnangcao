package com.library.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "borrowings")
@Getter
@NoArgsConstructor
public class Borrowing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "borrowed_at", nullable = false)
    private LocalDateTime borrowedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BorrowingStatus status = BorrowingStatus.BORROWING;

    @Enumerated(EnumType.STRING)
    @Column(name = "fine_status", nullable = false, length = 16)
    private FineStatus fineStatus = FineStatus.FINE_PAID;

    @Column(name = "returned_date")
    private LocalDateTime returnedDate;

    @Column(name = "total_fine_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal totalFineAmount = BigDecimal.ZERO;

    @Column(name = "paid_fine_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal paidFineAmount = BigDecimal.ZERO;

    @Column(name = "waived_fine_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal waivedFineAmount = BigDecimal.ZERO;

    @Column(name = "unpaid_fine_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal unpaidFineAmount = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private int version;

    @OneToMany(mappedBy = "borrowing", cascade = CascadeType.PERSIST)
    private List<BorrowingDetail> details = new ArrayList<>();

    public Borrowing(Member member, LocalDateTime borrowedAt, LocalDate dueDate, String notes) {
        this.member = member;
        this.borrowedAt = borrowedAt;
        this.dueDate = dueDate;
        this.notes = notes;
    }

    public void addDetail(BorrowingDetail detail) {
        details.add(detail);
    }

    public void cancel() {
        boolean hasReturnedCopies = details.stream().anyMatch(detail -> detail.getReturnedQuantity() > 0);
        if (status == BorrowingStatus.CANCELLED || status == BorrowingStatus.RETURNED
                || hasReturnedCopies || totalFineAmount.signum() > 0) {
            throw new IllegalStateException("Chỉ được hủy phiếu chưa trả sách và chưa phát sinh phí.");
        }
        status = BorrowingStatus.CANCELLED;
    }

    public void addFine(BigDecimal amount) {
        totalFineAmount = totalFineAmount.add(amount);
        unpaidFineAmount = unpaidFineAmount.add(amount);
        refreshFineStatus();
    }

    public void recordPayment(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(unpaidFineAmount) > 0) {
            throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ.");
        }
        paidFineAmount = paidFineAmount.add(amount);
        unpaidFineAmount = unpaidFineAmount.subtract(amount);
        refreshFineStatus();
    }

    public void recordWaiver(BigDecimal amount) {
        if (amount.signum() <= 0 || amount.compareTo(unpaidFineAmount) > 0) {
            throw new IllegalArgumentException("Số tiền miễn giảm không hợp lệ.");
        }
        waivedFineAmount = waivedFineAmount.add(amount);
        unpaidFineAmount = unpaidFineAmount.subtract(amount);
        refreshFineStatus();
    }

    public void refreshStatus(LocalDate today) {
        boolean hasReturned = details.stream().anyMatch(detail -> detail.getReturnedQuantity() > 0);
        boolean hasOutstanding = details.stream().anyMatch(detail -> !detail.isFullyReturned());
        if (!hasOutstanding) {
            status = BorrowingStatus.RETURNED;
            returnedDate = details.stream()
                    .map(BorrowingDetail::getLastReturnedDate)
                    .filter(date -> date != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        } else if (dueDate.isBefore(today)) {
            status = BorrowingStatus.OVERDUE;
            returnedDate = null;
        } else if (hasReturned) {
            status = BorrowingStatus.PARTIALLY_RETURNED;
            returnedDate = null;
        } else {
            status = BorrowingStatus.BORROWING;
            returnedDate = null;
        }
        refreshFineStatus();
    }

    private void refreshFineStatus() {
        fineStatus = unpaidFineAmount.signum() > 0 ? FineStatus.FINE_PENDING : FineStatus.FINE_PAID;
    }
}
