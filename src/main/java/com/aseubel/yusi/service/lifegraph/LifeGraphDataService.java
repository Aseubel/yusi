package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.lifegraph.dto.GraphSnapshotDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图谱数据CRUD服务，为3D可视化前端提供数据查询和编辑能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LifeGraphDataService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphRelationRepository relationRepository;

    // ======================== 查询 ========================

    /**
     * 分页获取全图数据
     */
    public GraphSnapshotDTO getFullGraph(String userId, int page, int size) {
        Page<LifeGraphEntity> entityPage = entityRepository.findByUserId(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "mentionCount")));

        List<LifeGraphEntity> entities = entityPage.getContent();
        Set<Long> entityIds = entities.stream().map(LifeGraphEntity::getId).collect(Collectors.toSet());

        // 获取这些节点之间的关系
        List<LifeGraphRelation> relations = new ArrayList<>();
        if (!entityIds.isEmpty()) {
            List<LifeGraphRelation> bySource = relationRepository.findByUserIdAndSourceIdIn(userId, entityIds);
            for (LifeGraphRelation r : bySource) {
                if (entityIds.contains(r.getTargetId())) {
                    relations.add(r);
                }
            }
        }

        long totalCount = entityRepository.countByUserId(userId);

        return GraphSnapshotDTO.builder()
                .nodes(entities.stream().map(this::toNodeDTO).toList())
                .links(relations.stream().map(this::toLinkDTO).toList())
                .totalNodeCount(totalCount)
                .build();
    }

    /**
     * BFS 从中心节点扩展，返回指定深度内的子图
     */
    public GraphSnapshotDTO getGraphBfs(String userId, Long centerId, int depth, int maxNodes) {
        Map<Long, LifeGraphEntity> visitedEntities = new LinkedHashMap<>();
        Set<Long> frontier = new HashSet<>();
        frontier.add(centerId);

        for (int d = 0; d <= depth && visitedEntities.size() < maxNodes && !frontier.isEmpty(); d++) {
            List<LifeGraphEntity> found = entityRepository.findAllById(frontier).stream()
                    .filter(e -> e.getUserId().equals(userId))
                    .toList();

            Set<Long> nextFrontier = new HashSet<>();
            for (LifeGraphEntity e : found) {
                if (visitedEntities.size() >= maxNodes) break;
                visitedEntities.put(e.getId(), e);
            }

            if (d < depth) {
                Set<Long> currentIds = found.stream().map(LifeGraphEntity::getId).collect(Collectors.toSet());
                List<LifeGraphRelation> outRels = relationRepository.findByUserIdAndSourceIdIn(userId, currentIds);
                List<LifeGraphRelation> inRels = relationRepository.findByUserIdAndTargetIdIn(userId, currentIds);

                for (LifeGraphRelation r : outRels) {
                    if (!visitedEntities.containsKey(r.getTargetId())) nextFrontier.add(r.getTargetId());
                }
                for (LifeGraphRelation r : inRels) {
                    if (!visitedEntities.containsKey(r.getSourceId())) nextFrontier.add(r.getSourceId());
                }
            }
            frontier = nextFrontier;
        }

        Set<Long> nodeIds = visitedEntities.keySet();
        List<LifeGraphRelation> relations = new ArrayList<>();
        if (!nodeIds.isEmpty()) {
            List<LifeGraphRelation> bySource = relationRepository.findByUserIdAndSourceIdIn(userId, nodeIds);
            for (LifeGraphRelation r : bySource) {
                if (nodeIds.contains(r.getTargetId())) {
                    relations.add(r);
                }
            }
        }

        long totalCount = entityRepository.countByUserId(userId);

        return GraphSnapshotDTO.builder()
                .nodes(visitedEntities.values().stream().map(this::toNodeDTO).toList())
                .links(relations.stream().map(this::toLinkDTO).toList())
                .totalNodeCount(totalCount)
                .build();
    }

    // ======================== Entity CRUD ========================

    @Transactional
    public LifeGraphEntity createEntity(String userId, String displayName, String type, String summary, String props) {
        String nameNorm = normalize(displayName);
        LifeGraphEntity.EntityType entityType = LifeGraphEntity.EntityType.valueOf(type);

        LifeGraphEntity entity = LifeGraphEntity.builder()
                .userId(userId)
                .type(entityType)
                .nameNorm(nameNorm)
                .displayName(displayName)
                .mentionCount(0)
                .relationCount(0)
                .summary(summary)
                .props(props)
                .build();

        return entityRepository.save(entity);
    }

    @Transactional
    public LifeGraphEntity updateEntity(String userId, Long entityId, String displayName, String type, String summary,
                                         String props, Long version) {
        LifeGraphEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + entityId));

        if (!entity.getUserId().equals(userId)) {
            throw new SecurityException("Cannot edit entity owned by another user");
        }

        // 乐观锁版本校验
        if (version != null && !version.equals(entity.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(LifeGraphEntity.class, entityId);
        }

        boolean changedIdentity = false;
        if (StrUtil.isNotBlank(type)) {
            LifeGraphEntity.EntityType newType = LifeGraphEntity.EntityType.valueOf(type);
            if (entity.getType() != newType) {
                entity.setType(newType);
                changedIdentity = true;
            }
        }

        if (StrUtil.isNotBlank(displayName)) {
            String newNorm = normalize(displayName);
            if (!newNorm.equals(entity.getNameNorm()) || !displayName.equals(entity.getDisplayName())) {
                entity.setDisplayName(displayName);
                entity.setNameNorm(newNorm);
                changedIdentity = true;
            }
        }
        if (summary != null) {
            entity.setSummary(summary);
        }
        if (props != null) {
            entity.setProps(props);
        }

        final LifeGraphEntity savedEntity = entityRepository.save(entity);

        if (changedIdentity) {
            List<LifeGraphEntity> siblings = entityRepository.findByUserIdAndNameNorm(userId, savedEntity.getNameNorm());
            LifeGraphEntity target = siblings.stream()
                    .filter(e -> e.getType() == savedEntity.getType() && !e.getId().equals(savedEntity.getId()))
                    .findFirst()
                    .orElse(null);

            if (target != null) {
                return performMerge(savedEntity, target);
            }
        }

        return savedEntity;
    }

    private LifeGraphEntity performMerge(LifeGraphEntity source, LifeGraphEntity target) {
        // Merge counters and summaries
        target.setMentionCount((target.getMentionCount() == null ? 0 : target.getMentionCount()) 
                + (source.getMentionCount() == null ? 0 : source.getMentionCount()));
        
        target.setRelationCount((target.getRelationCount() == null ? 0 : target.getRelationCount()) 
                + (source.getRelationCount() == null ? 0 : source.getRelationCount()));

        if (StrUtil.isBlank(target.getSummary()) && StrUtil.isNotBlank(source.getSummary())) {
            target.setSummary(source.getSummary());
        } else if (StrUtil.isNotBlank(target.getSummary()) && StrUtil.isNotBlank(source.getSummary())) {
            target.setSummary(target.getSummary() + "\n" + source.getSummary());
        }

        // Redirect relations
        List<LifeGraphRelation> outRels = relationRepository.findByUserIdAndSourceIdIn(source.getUserId(), List.of(source.getId()));
        for (LifeGraphRelation r : outRels) {
            r.setSourceId(target.getId());
        }
        relationRepository.saveAll(outRels);

        List<LifeGraphRelation> inRels = relationRepository.findByUserIdAndTargetIdIn(source.getUserId(), List.of(source.getId()));
        for (LifeGraphRelation r : inRels) {
            r.setTargetId(target.getId());
        }
        relationRepository.saveAll(inRels);

        entityRepository.save(target);
        entityRepository.delete(source);

        return target;
    }

    @Transactional
    public void deleteEntity(String userId, Long entityId) {
        LifeGraphEntity entity = entityRepository.findById(entityId)
                .orElseThrow(() -> new EntityNotFoundException("Entity not found: " + entityId));

        if (!entity.getUserId().equals(userId)) {
            throw new SecurityException("Cannot delete entity owned by another user");
        }

        // 删除关联的关系
        List<LifeGraphRelation> srcRels = relationRepository.findByUserIdAndSourceIdIn(userId, List.of(entityId));
        List<LifeGraphRelation> tgtRels = relationRepository.findByUserIdAndTargetIdIn(userId, List.of(entityId));
        relationRepository.deleteAll(srcRels);
        relationRepository.deleteAll(tgtRels);

        entityRepository.delete(entity);
    }

    // ======================== Relation CRUD ========================

    @Transactional
    public LifeGraphRelation createRelation(String userId, Long sourceId, Long targetId, String type,
                                             BigDecimal confidence, Integer weight) {
        // 验证节点存在且属于当前用户
        entityRepository.findById(sourceId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Source entity not found: " + sourceId));
        entityRepository.findById(targetId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Target entity not found: " + targetId));

        LifeGraphRelation relation = LifeGraphRelation.builder()
                .userId(userId)
                .sourceId(sourceId)
                .targetId(targetId)
                .type(type)
                .confidence(confidence != null ? confidence : BigDecimal.valueOf(0.8))
                .weight(weight != null ? weight : 1)
                .build();

        return relationRepository.save(relation);
    }

    @Transactional
    public LifeGraphRelation updateRelation(String userId, Long relationId, String type,
                                             BigDecimal confidence, Integer weight, Long version) {
        LifeGraphRelation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new EntityNotFoundException("Relation not found: " + relationId));

        if (!relation.getUserId().equals(userId)) {
            throw new SecurityException("Cannot edit relation owned by another user");
        }

        if (version != null && !version.equals(relation.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(LifeGraphRelation.class, relationId);
        }

        if (StrUtil.isNotBlank(type)) {
            relation.setType(type);
        }
        if (confidence != null) {
            relation.setConfidence(confidence);
        }
        if (weight != null) {
            relation.setWeight(weight);
        }

        return relationRepository.save(relation);
    }

    @Transactional
    public void deleteRelation(String userId, Long relationId) {
        LifeGraphRelation relation = relationRepository.findById(relationId)
                .orElseThrow(() -> new EntityNotFoundException("Relation not found: " + relationId));

        if (!relation.getUserId().equals(userId)) {
            throw new SecurityException("Cannot delete relation owned by another user");
        }

        relationRepository.delete(relation);
    }

    // ======================== Helpers ========================

    private GraphSnapshotDTO.NodeDTO toNodeDTO(LifeGraphEntity e) {
        return GraphSnapshotDTO.NodeDTO.builder()
                .id(e.getId())
                .displayName(e.getDisplayName())
                .type(e.getType().name())
                .mentionCount(e.getMentionCount())
                .summary(e.getSummary())
                .props(e.getProps())
                .version(e.getVersion())
                .build();
    }

    private GraphSnapshotDTO.LinkDTO toLinkDTO(LifeGraphRelation r) {
        return GraphSnapshotDTO.LinkDTO.builder()
                .id(r.getId())
                .sourceId(r.getSourceId())
                .targetId(r.getTargetId())
                .type(r.getType())
                .confidence(r.getConfidence())
                .weight(r.getWeight())
                .version(r.getVersion())
                .build();
    }

    private String normalize(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("\\s+", "");
        return s.toLowerCase();
    }
}
