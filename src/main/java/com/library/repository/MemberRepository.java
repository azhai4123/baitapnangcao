package com.library.repository;

import com.library.entity.Member;
import com.library.entity.MemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByStatusOrderByFullNameAsc(MemberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select member from Member member where member.id = :id")
    Optional<Member> findByIdForBorrowing(@Param("id") Long id);
}
