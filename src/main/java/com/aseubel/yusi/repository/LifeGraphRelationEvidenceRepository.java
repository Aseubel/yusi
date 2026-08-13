package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LifeGraphRelationEvidenceRepository extends JpaRepository<LifeGraphRelationEvidence, Long> {

    List<LifeGraphRelationEvidence> findByUserIdAndRelationId(String userId, Long relationId);

    java.util.Optional<LifeGraphRelationEvidence> findByUserIdAndRelationIdAndSourceTypeAndSourceId(
            String userId, Long relationId, String sourceType, String sourceId);

    List<LifeGraphRelationEvidence> findByUserIdAndSourceTypeAndSourceId(
            String userId, String sourceType, String sourceId);

    int deleteByUserIdAndSourceTypeAndSourceId(String userId, String sourceType, String sourceId);

    int deleteByUserIdAndRelationId(String userId, Long relationId);

    int deleteByUserIdAndRelationIdIn(String userId, Collection<Long> relationIds);
}
