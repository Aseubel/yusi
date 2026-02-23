package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphMergeJudgment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface LifeGraphMergeJudgmentRepository extends JpaRepository<LifeGraphMergeJudgment, Long> {

    /**
     * 查找两个实体之间的判断记录（无论顺序）
     */
    @Query("SELECT j FROM LifeGraphMergeJudgment j WHERE j.userId = :userId " +
           "AND ((j.entityIdA = :idA AND j.entityIdB = :idB) OR (j.entityIdA = :idB AND j.entityIdB = :idA))")
    Optional<LifeGraphMergeJudgment> findByEntityPair(@Param("userId") String userId,
                                                       @Param("idA") Long idA,
                                                       @Param("idB") Long idB);

    /**
     * 查找用户所有已判断的实体对ID（用于排除）
     * 返回格式：每对实体中较小的ID在前
     */
    @Query("SELECT CONCAT(LEAST(j.entityIdA, j.entityIdB), '-', GREATEST(j.entityIdA, j.entityIdB)) " +
           "FROM LifeGraphMergeJudgment j WHERE j.userId = :userId")
    Set<String> findJudgedPairs(@Param("userId") String userId);

    /**
     * 查找用户待处理的合并建议
     */
    List<LifeGraphMergeJudgment> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);

    /**
     * 查找用户所有判断记录
     */
    List<LifeGraphMergeJudgment> findByUserIdOrderByCreatedAtDesc(String userId);
}
