package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.UserPersona;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserPersonaRepository extends JpaRepository<UserPersona, Long> {
    Optional<UserPersona> findByUserId(String userId);

    @Query("""
            SELECT p FROM UserPersona p
            WHERE p.userId = :userId
              AND p.hidden = false
              AND (p.validUntil IS NULL OR p.validUntil > :now)
            """)
    Optional<UserPersona> findVisibleByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Query("""
            SELECT p FROM UserPersona p
            WHERE p.userId = :userId
              AND p.hidden = false
              AND p.matchAllowed = true
              AND (p.validUntil IS NULL OR p.validUntil > :now)
            """)
    Optional<UserPersona> findMatchableByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
