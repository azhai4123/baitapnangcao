package com.library.repository;

import com.library.entity.FinePayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinePaymentRepository extends JpaRepository<FinePayment, Long> {

    List<FinePayment> findAllByBorrowingIdOrderByPaymentDateDesc(Long borrowingId);
}