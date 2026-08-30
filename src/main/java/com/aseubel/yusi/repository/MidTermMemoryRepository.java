package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.MidTermMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MidTermMemoryRepository
        extends JpaRepository<MidTermMemory, Long>, JpaSpecificationExecutor<MidTermMemory> {

    List<MidTermMemory> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<MidTermMemory> findByUserId(String userId);

    java.util.Optional<MidTermMemory> findByIdAndUserId(Long id, String userId);

    List<MidTermMemory> findByUserIdAndSourceTypeAndSourceId(String userId, String sourceType, String sourceId);

    /**
     * 查找有效的中期记忆（未被遗忘），按创建时间倒序。
     * 使用显式 JPQL 避免 Spring Data 方法名解析的 And/Or 优先级陷阱。
     */
    @Query("SELECT m FROM MidTermMemory m WHERE m.userId = :userId AND m.forgottenAt IS NULL AND m.mergedIntoId IS NULL AND m.hidden = false ORDER BY m.createdAt DESC")
    List<MidTermMemory> findValidByUserId(@Param("userId") String userId, Pageable pageable);

    /** 可参与匹配的有效记忆：隐藏、遗忘、已融合或未授权的记忆都排除。 */
    @Query("SELECT m FROM MidTermMemory m WHERE m.userId = :userId AND m.forgottenAt IS NULL AND m.mergedIntoId IS NULL AND m.hidden = false AND m.matchAllowed = true ORDER BY m.createdAt DESC")
    List<MidTermMemory> findMatchableByUserId(@Param("userId") String userId, Pageable pageable);

    /** 用于生成新的摘要上下文，只读取未隐藏且未被遗忘的记忆。 */
    @Query("SELECT m FROM MidTermMemory m WHERE m.userId = :userId AND m.forgottenAt IS NULL AND m.mergedIntoId IS NULL AND m.hidden = false ORDER BY m.createdAt DESC")
    List<MidTermMemory> findAvailableByUserId(@Param("userId") String userId, Pageable pageable);

    /** 用于跨源融合：获取用户最近的有效且未合并的中期记忆，便于两两比较去重 */
    @Query("SELECT m FROM MidTermMemory m WHERE m.userId = :userId AND m.forgottenAt IS NULL AND m.mergedIntoId IS NULL AND m.hidden = false ORDER BY m.createdAt DESC")
    List<MidTermMemory> findUnmergedByUserId(@Param("userId") String userId);

}
