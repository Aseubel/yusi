package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LifeGraphEntityEvidenceRepository extends JpaRepository<LifeGraphEntityEvidence, Long> {

    List<LifeGraphEntityEvidence> findByUserIdAndEntityId(String userId, Long entityId);

    List<LifeGraphEntityEvidence> findByUserIdAndSourceTypeAndSourceId(
            String userId, String sourceType, String sourceId);

    Optional<LifeGraphEntityEvidence> findByUserIdAndEntityIdAndSourceTypeAndSourceId(
            String userId, Long entityId, String sourceType, String sourceId);

    int deleteByUserIdAndSourceTypeAndSourceId(String userId, String sourceType, String sourceId);
}
