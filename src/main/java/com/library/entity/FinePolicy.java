package com.library.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fine_policies")
@Getter
@NoArgsConstructor
public class FinePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_fine_amount", nullable = false, precision = 12, scale = 0)
    private BigDecimal dailyFineAmount;

    @Column(name = "max_fine_amount", precision = 12, scale = 0)
    private BigDecimal maxFineAmount;

    @Column(name = "grace_days", nullable = false)
    private int graceDays;

    @Column(nullable = false)
    private boolean active;

    public FinePolicy(BigDecimal dailyFineAmount, BigDecimal maxFineAmount, int graceDays, boolean active) {
        this.dailyFineAmount = dailyFineAmount;
        this.maxFineAmount = maxFineAmount;
        this.graceDays = graceDays;
        this.active = active;
    }

    public void deactivate() {
        active = false;
    }
}