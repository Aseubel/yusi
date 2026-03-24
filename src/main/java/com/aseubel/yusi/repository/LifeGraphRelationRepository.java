package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LifeGraphRelationRepository extends JpaRepository<LifeGraphRelation, Long> {
    Optional<LifeGraphRelation> findByUserIdAndSourceIdAndTargetIdAndType(String userId, Long sourceId, Long targetId,
            String type);

    List<LifeGraphRelation> findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc(String userId, Long sourceId);
    List<LifeGraphRelation> findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc(String userId, Long targetId);

    int deleteByUserIdAndEvidenceDiaryId(String userId, String evidenceDiaryId);

    List<LifeGraphRelation> findByUserIdAndSourceIdIn(String userId, Collection<Long> sourceIds);
    List<LifeGraphRelation> findByUserIdAndTargetIdIn(String userId, Collection<Long> targetIds);

    List<LifeGraphRelation> findByUserId(String userId);

    void deleteByUserIdAndSourceIdOrTargetId(String userId, Long sourceId, Long targetId);
}

