package com.library.repository;

import com.library.entity.FinePolicy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface FinePolicyRepository extends JpaRepository<FinePolicy, Long> {

    Optional<FinePolicy> findFirstByActiveTrueOrderByIdAsc();

    List<FinePolicy> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from FinePolicy policy where policy.active = true")
    List<FinePolicy> lockAllActive();
}