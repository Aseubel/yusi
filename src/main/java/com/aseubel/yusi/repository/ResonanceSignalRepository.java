package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ResonanceSignal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResonanceSignalRepository extends JpaRepository<ResonanceSignal, Long> {

    /** 查用户收到的共鸣信号 */
    List<ResonanceSignal> findByToUserIdOrderByCreatedAtDesc(String toUserId, Pageable pageable);

    /** 查用户发送的共鸣信号 */
    List<ResonanceSignal> findByFromUserIdOrderByCreatedAtDesc(String fromUserId, Pageable pageable);

    /** 检查是否已有相互信号 */
    Optional<ResonanceSignal> findByFromUserIdAndToUserId(String fromUserId, String toUserId);

    /** 统计用户收到的未读信号 */
    long countByToUserIdAndIsReadFalse(String toUserId);
}
