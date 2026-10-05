package com.library.repository;

import com.library.entity.FineWaiver;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FineWaiverRepository extends JpaRepository<FineWaiver, Long> {

    List<FineWaiver> findAllByBorrowingIdOrderByApprovedDateDesc(Long borrowingId);
}