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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fine_waivers")
@Getter
@NoArgsConstructor
public class FineWaiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrowing_id", nullable = false)
    private Borrowing borrowing;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "approved_by", nullable = false, length = 200)
    private String approvedBy;

    @Column(name = "approved_date", nullable = false)
    private LocalDateTime approvedDate;

    public FineWaiver(
            Borrowing borrowing, BigDecimal amount, String reason, String approvedBy, LocalDateTime approvedDate) {
        this.borrowing = borrowing;
        this.amount = amount;
        this.reason = reason;
        this.approvedBy = approvedBy;
        this.approvedDate = approvedDate;
    }
}