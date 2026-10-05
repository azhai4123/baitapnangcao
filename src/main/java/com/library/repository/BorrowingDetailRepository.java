package com.library.repository;

import com.library.entity.BorrowingDetail;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BorrowingDetailRepository extends JpaRepository<BorrowingDetail, Long> {

    @EntityGraph(attributePaths = {"borrowing", "borrowing.member", "book"})
    List<BorrowingDetail> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"borrowing", "borrowing.member", "book"})
    @Query("""
            select detail
            from BorrowingDetail detail
                        where detail.returnedAt is null
                            and detail.borrowing.status <> com.library.entity.BorrowingStatus.CANCELLED
                            and detail.borrowing.dueDate < :date
            """)
    List<BorrowingDetail> findAllOverdue(@Param("date") LocalDate date);
}
