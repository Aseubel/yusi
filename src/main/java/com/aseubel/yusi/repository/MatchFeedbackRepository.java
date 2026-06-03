package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.MatchFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchFeedbackRepository extends JpaRepository<MatchFeedback, Long> {

    /** 查询用户最近的匹配反馈（用于精排时的偏好上下文） */
    List<MatchFeedback> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

    /** 统计用户某类反馈数量 */
    long countByUserIdAndAction(String userId, String action);
}
