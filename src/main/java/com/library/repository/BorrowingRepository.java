package com.library.repository;

import com.library.entity.Borrowing;
import com.library.entity.BorrowingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;

public interface BorrowingRepository extends JpaRepository<Borrowing, Long> {

    @EntityGraph(attributePaths = {"member", "details", "details.book"})
    Optional<Borrowing> findDetailedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select borrowing from Borrowing borrowing where borrowing.id = :id")
    Optional<Borrowing> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"member", "details", "details.book"})
    List<Borrowing> findAllByOrderByBorrowedAtDesc();

        boolean existsByMember_IdAndUnpaidFineAmountGreaterThan(Long memberId, java.math.BigDecimal amount);

        @Query("""
                        select case when count(detail) > 0 then true else false end
                        from BorrowingDetail detail
                        where detail.borrowing.member.id = :memberId
                            and detail.borrowing.status <> com.library.entity.BorrowingStatus.CANCELLED
                            and detail.borrowing.dueDate < :today
                            and detail.returnedQuantity < detail.quantity
                        """)
        boolean hasOverdueBooks(@Param("memberId") Long memberId, @Param("today") LocalDate today);

        @Query("""
                        select coalesce(sum(detail.quantity - detail.returnedQuantity), 0)
                        from BorrowingDetail detail
                        where detail.borrowing.member.id = :memberId
                            and detail.borrowing.status <> com.library.entity.BorrowingStatus.CANCELLED
                            and detail.returnedQuantity < detail.quantity
                        """)
        long countOutstandingCopies(@Param("memberId") Long memberId);
}
