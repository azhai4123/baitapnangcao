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
@Table(name = "fine_payments")
@Getter
@NoArgsConstructor
public class FinePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrowing_id", nullable = false)
    private Borrowing borrowing;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Column(nullable = false, length = 32)
    private String method;

    @Column(length = 500)
    private String note;

    public FinePayment(Borrowing borrowing, BigDecimal amount, LocalDateTime paymentDate, String method, String note) {
        this.borrowing = borrowing;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.method = method;
        this.note = note;
    }
}