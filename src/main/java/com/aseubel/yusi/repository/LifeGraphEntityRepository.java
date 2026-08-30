package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LifeGraphEntityRepository extends JpaRepository<LifeGraphEntity, Long> {
    Optional<LifeGraphEntity> findByUserIdAndTypeAndNameNorm(String userId, LifeGraphEntity.EntityType type,
            String nameNorm);

    List<LifeGraphEntity> findByUserIdAndNameNorm(String userId, String nameNorm);

    Page<LifeGraphEntity> findByUserIdAndDisplayNameContainingOrderByMentionCountDesc(String userId, String displayName,
            Pageable pageable);

    List<LifeGraphEntity> findTop50ByUserIdOrderByMentionCountDesc(String userId);

    List<LifeGraphEntity> findByUserIdAndType(String userId, LifeGraphEntity.EntityType type);

    Page<LifeGraphEntity> findByUserId(String userId, Pageable pageable);

    long countByUserId(String userId);

    List<LifeGraphEntity> findByUserId(String userId);

    @Query("""
            SELECT e FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
            """)
    Page<LifeGraphEntity> findVisibleByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("""
            SELECT COUNT(e) FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
            """)
    long countVisibleByUserId(@Param("userId") String userId);

    @Query("""
            SELECT e FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
              AND e.type = :type
            """)
    Page<LifeGraphEntity> findVisibleByUserIdAndType(@Param("userId") String userId,
            @Param("type") LifeGraphEntity.EntityType type, Pageable pageable);

    @Query("""
            SELECT e FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
              AND e.type = :type
            ORDER BY e.mentionCount DESC, e.updatedAt DESC
            """)
    List<LifeGraphEntity> findAllVisibleByUserIdAndType(@Param("userId") String userId,
            @Param("type") LifeGraphEntity.EntityType type);

    @Query("""
            SELECT COUNT(e) FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
              AND e.type = :type
            """)
    long countVisibleByUserIdAndType(@Param("userId") String userId,
            @Param("type") LifeGraphEntity.EntityType type);

    @Query("""
            SELECT e FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
              AND LOWER(e.displayName) LIKE LOWER(CONCAT('%', :displayName, '%'))
            ORDER BY e.mentionCount DESC, e.updatedAt DESC
            """)
    Page<LifeGraphEntity> findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
            @Param("userId") String userId, @Param("displayName") String displayName, Pageable pageable);

    @Query("""
            SELECT e FROM LifeGraphEntity e
            WHERE e.userId = :userId
              AND e.hidden = false
              AND e.matchAllowed = true
            ORDER BY COALESCE(e.importance, 0.5) DESC,
                     COALESCE(e.mentionCount, 0) DESC,
                     e.updatedAt DESC,
                     e.id ASC
            """)
    List<LifeGraphEntity> findMatchableTopByUserId(@Param("userId") String userId, Pageable pageable);

    Optional<LifeGraphEntity> findByIdAndUserId(Long id, String userId);
}
