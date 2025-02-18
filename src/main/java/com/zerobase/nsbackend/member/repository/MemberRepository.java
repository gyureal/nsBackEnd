package com.zerobase.nsbackend.member.repository;

import com.zerobase.nsbackend.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member,Long> {
    boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);
}
