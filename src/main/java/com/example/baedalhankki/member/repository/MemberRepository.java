package com.example.baedalhankki.member.repository;

import com.example.baedalhankki.member.entity.Member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findForOrderCreation(@Param("id") Long id);

    Optional<Member> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
