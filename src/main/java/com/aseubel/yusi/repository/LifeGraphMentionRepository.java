package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LifeGraphMentionRepository extends JpaRepository<LifeGraphMention, Long> {
    List<LifeGraphMention> findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(String userId, Long entityId);

    List<LifeGraphMention> findByUserIdAndDiaryId(String userId, String diaryId);

    List<LifeGraphMention> findByUserIdAndEntityIdAndDiaryId(String userId, Long entityId, String diaryId);

    List<LifeGraphMention> findByUserIdAndEntityId(String userId, Long entityId);

    int deleteByUserIdAndDiaryId(String userId, String diaryId);

    int deleteByUserIdAndEntityId(String userId, Long entityId);
}
