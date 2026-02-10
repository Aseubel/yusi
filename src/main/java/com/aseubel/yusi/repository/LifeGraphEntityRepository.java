package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
