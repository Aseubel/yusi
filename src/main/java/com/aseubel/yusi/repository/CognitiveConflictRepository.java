package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.CognitiveConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CognitiveConflictRepository extends JpaRepository<CognitiveConflict, Long> {

    /** 查询用户未解决的冲突（供 Agent 对话注入） */
    List<CognitiveConflict> findByUserIdAndResolvedFalseOrderByCreatedAtDesc(String userId);

    /** 查询用户最近创建的冲突（去重检测） */
    List<CognitiveConflict> findTop3ByUserIdOrderByCreatedAtDesc(String userId);

    /** 统计未解决冲突数 */
    long countByUserIdAndResolvedFalse(String userId);
}
