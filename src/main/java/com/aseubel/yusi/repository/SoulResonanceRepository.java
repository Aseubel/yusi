package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.SoulResonance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoulResonanceRepository extends JpaRepository<SoulResonance, Long> {

    boolean existsByCardIdAndUserId(Long cardId, String userId);

    /**
     * 查找用户的所有共鸣记录（用于分析情感偏好）
     */
    List<SoulResonance> findByUserId(String userId);
}
