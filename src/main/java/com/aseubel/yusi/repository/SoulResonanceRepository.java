package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulResonance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoulResonanceRepository extends JpaRepository<SoulResonance, Long> {

    boolean existsByCardIdAndUserId(Long cardId, String userId);
}
