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

    /**
     * 查找有效的（未过期的）中期记忆，按创建时间倒序。
     * 使用显式 JPQL 避免 Spring Data 方法名解析的 And/Or 优先级陷阱。
     */
    @Query("SELECT m FROM MidTermMemory m WHERE m.userId = :userId AND (m.validUntil > :now OR m.validUntil IS NULL) AND m.mergedIntoId IS NULL ORDER BY m.createdAt DESC")
    List<MidTermMemory> findValidByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now, Pageable pageable);

    /** 用于跨源融合：获取用户最近的有效且未合并的中期记忆，便于两两比较去重 */
    @Query("SELECT m FROM MidTermMemory m WHERE m.userId = :userId AND (m.validUntil > :now OR m.validUntil IS NULL) AND m.mergedIntoId IS NULL ORDER BY m.createdAt DESC")
    List<MidTermMemory> findUnmergedByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

}
