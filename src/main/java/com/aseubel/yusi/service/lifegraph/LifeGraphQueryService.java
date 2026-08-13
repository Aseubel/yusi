package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityAlias;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationEvidenceRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evidence-aware local GraphRAG search.
 *
 * <p>The traversal has runtime budgets but no product-level one-hop limit.
 * The one-hop rule belongs to source promotion, not retrieval.</p>
 */
@Service
public class LifeGraphQueryService {

    private static final Set<String> LANGUAGE_ONLY_RELATIONS = Set.of(
            "MENTIONED", "MENTIONED_IN", "SAID", "RELATED_TO");
    private static final Set<String> AUTOMATIC_SOURCE_TYPES = Set.of("DIARY", "PLAZA");
    private static final double MIN_EVIDENCE_CONFIDENCE = 0.6;

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final LifeGraphRelationEvidenceRepository evidenceRepository;
    private final LifeGraphEntityEvidenceRepository entityEvidenceRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public LifeGraphQueryService(LifeGraphEntityRepository entityRepository,
                                 LifeGraphEntityAliasRepository aliasRepository,
                                 LifeGraphRelationRepository relationRepository,
                                 LifeGraphMentionRepository mentionRepository,
                                 LifeGraphRelationEvidenceRepository evidenceRepository,
                                 LifeGraphEntityEvidenceRepository entityEvidenceRepository,
                                 ObjectMapper objectMapper) {
        this.entityRepository = entityRepository;
        this.aliasRepository = aliasRepository;
        this.relationRepository = relationRepository;
        this.mentionRepository = mentionRepository;
        this.evidenceRepository = evidenceRepository;
        this.entityEvidenceRepository = entityEvidenceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Compatibility constructor for callers that do not have relation evidence
     * wired yet. Automatic relations are then treated as unavailable.
     */
    public LifeGraphQueryService(LifeGraphEntityRepository entityRepository,
                                 LifeGraphEntityAliasRepository aliasRepository,
                                 LifeGraphRelationRepository relationRepository,
                                 LifeGraphMentionRepository mentionRepository,
                                 ObjectMapper objectMapper) {
        this(entityRepository, aliasRepository, relationRepository, mentionRepository, null, null, objectMapper);
    }

    public String localSearch(String userId, String query, int maxEntities, int maxRelations, int maxMentions) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(query)) {
            return "";
        }

        int entityBudget = Math.max(1, maxEntities);
        int relationBudget = Math.max(0, maxRelations);
        int mentionBudget = Math.max(0, maxMentions);
        LocalDateTime now = LocalDateTime.now();
        Map<Long, LifeGraphEntity> entities = findSeedEntities(userId, query.trim(), entityBudget, now);
        if (entities.isEmpty()) {
            return "";
        }

        Set<Long> visited = new LinkedHashSet<>(entities.keySet());
        Queue<Long> frontier = new ArrayDeque<>(entities.keySet());
        Map<Long, LifeGraphRelation> relations = new LinkedHashMap<>();
        Set<String> relationKeys = new HashSet<>();

        while (!frontier.isEmpty() && visited.size() < entityBudget && relations.size() < relationBudget) {
            Long currentId = frontier.poll();
            List<LifeGraphRelation> adjacent = new ArrayList<>();
            adjacent.addAll(safeRelations(
                    relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc(userId, currentId)));
            adjacent.addAll(safeRelations(
                    relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc(userId, currentId)));

            Set<Long> neighborIds = new LinkedHashSet<>();
            for (LifeGraphRelation relation : adjacent) {
                if (!isUsableRelation(userId, relation, now)) {
                    continue;
                }
                String relationKey = relationKey(relation);
                if (!relationKeys.add(relationKey)) {
                    continue;
                }
                Long neighborId = neighborId(relation, currentId);
                if (neighborId == null) {
                    continue;
                }
                relations.putIfAbsent(relation.getId(), relation);
                neighborIds.add(neighborId);
                if (relations.size() >= relationBudget) {
                    break;
                }
            }

            if (neighborIds.isEmpty() || visited.size() >= entityBudget) {
                continue;
            }
            List<LifeGraphEntity> found = safeEntities(entityRepository.findAllById(neighborIds));
            for (LifeGraphEntity entity : found) {
                if (!userId.equals(entity.getUserId()) || !isVisible(entity, now)) {
                    continue;
                }
                if (visited.add(entity.getId())) {
                    entities.put(entity.getId(), entity);
                    frontier.add(entity.getId());
                    if (visited.size() >= entityBudget) {
                        break;
                    }
                }
            }
        }

        List<LifeGraphRelation> selectedRelations = relations.values().stream()
                .filter(relation -> entities.containsKey(physicalSourceId(relation))
                        && entities.containsKey(physicalTargetId(relation)))
                .limit(relationBudget)
                .toList();

        List<EntitySource> sources = collectSources(userId, visited, mentionBudget);
        return render(entities, selectedRelations, sources);
    }

    private Map<Long, LifeGraphEntity> findSeedEntities(String userId, String query,
                                                         int maxEntities, LocalDateTime now) {
        String normalized = normalize(query);
        Map<Long, LifeGraphEntity> entities = new LinkedHashMap<>();
        aliasRepository.findByUserIdAndAliasNorm(userId, normalized)
                .flatMap(alias -> entityRepository.findByIdAndUserId(alias.getEntityId(), userId))
                .filter(entity -> isVisible(entity, now))
                .ifPresent(entity -> entities.put(entity.getId(), entity));

        for (LifeGraphEntity entity : entityRepository
                .findVisibleByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
                        userId, query, now, PageRequest.of(0, maxEntities)).getContent()) {
            if (isVisible(entity, now)) {
                entities.putIfAbsent(entity.getId(), entity);
            }
            if (entities.size() >= maxEntities) {
                break;
            }
        }
        return entities;
    }

    private boolean isUsableRelation(String userId, LifeGraphRelation relation, LocalDateTime now) {
        if (relation == null || relation.getType() == null
                || LANGUAGE_ONLY_RELATIONS.contains(relation.getType().trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        LifeGraphEntity source = entityRepository.findByIdAndUserId(physicalSourceId(relation), userId).orElse(null);
        LifeGraphEntity target = entityRepository.findByIdAndUserId(physicalTargetId(relation), userId).orElse(null);
        if (!isVisible(source, now) || !isVisible(target, now)) {
            return false;
        }
        if (relation.getOrigin() == LifeGraphRelation.Origin.AUTO) {
            if (relation.getConfidence() == null
                    || relation.getConfidence().compareTo(BigDecimal.valueOf(MIN_EVIDENCE_CONFIDENCE)) < 0) {
                return false;
            }
            if (evidenceRepository == null || relation.getId() == null) {
                return false;
            }
            return safeEvidence(evidenceRepository.findByUserIdAndRelationId(userId, relation.getId())).stream()
                    .anyMatch(this::isUsableAutomaticEvidence);
        }
        return true;
    }

    private Long neighborId(LifeGraphRelation relation, Long currentId) {
        Long source = physicalSourceId(relation);
        Long target = physicalTargetId(relation);
        if (currentId.equals(source)) {
            return target;
        }
        if (currentId.equals(target)) {
            return source;
        }
        return null;
    }

    private String render(Map<Long, LifeGraphEntity> entities,
                          List<LifeGraphRelation> relations,
                          List<EntitySource> sources) {
        StringBuilder output = new StringBuilder("GRAPH_ENTITIES:\n");
        for (LifeGraphEntity entity : entities.values()) {
            output.append("- ").append(entity.getType()).append(": ")
                    .append(entity.getDisplayName()).append(" (norm=")
                    .append(entity.getNameNorm()).append(")");
            if (StrUtil.isNotBlank(entity.getSummary())) {
                output.append("\n  summary: ").append(entity.getSummary());
            }
            String props = formatProps(entity.getProps());
            if (StrUtil.isNotBlank(props)) {
                output.append("\n  props: ").append(props);
            }
            output.append("\n");
        }

        output.append("GRAPH_RELATIONS:\n");
        for (LifeGraphRelation relation : relations) {
            LifeGraphEntity source = entities.get(semanticSourceId(relation));
            LifeGraphEntity target = entities.get(semanticTargetId(relation));
            if (source == null || target == null) {
                source = entities.get(physicalSourceId(relation));
                target = entities.get(physicalTargetId(relation));
            }
            if (source == null || target == null) {
                continue;
            }
            output.append("- ").append(source.getDisplayName()).append(" -> ")
                    .append(target.getDisplayName()).append(" [").append(relation.getType()).append("]")
                    .append(" conf=").append(relation.getConfidence())
                    .append(" weight=").append(relation.getWeight());
            String sourceMetadata = relationSourceMetadata(relation);
            if (StrUtil.isNotBlank(sourceMetadata)) {
                output.append(" evidence=").append(sourceMetadata);
            }
            output.append("\n");
        }

        output.append("GRAPH_SOURCES:\n");
        for (EntitySource source : sources) {
            LifeGraphEntity entity = entities.get(source.entityId());
            if (entity == null) {
                continue;
            }
            output.append("- ").append(entity.getDisplayName())
                    .append(": source=").append(source.sourceType()).append(":").append(source.sourceId());
            if (source.entryDate() != null) {
                output.append(", date=").append(source.entryDate());
            }
            output.append("\n");
        }
        return output.toString();
    }

    private String relationSourceMetadata(LifeGraphRelation relation) {
        if (evidenceRepository == null || relation.getId() == null) {
            return null;
        }
        return safeEvidence(evidenceRepository.findByUserIdAndRelationId(
                        relation.getUserId(), relation.getId())).stream()
                .filter(this::isUsableAutomaticEvidence)
                .map(evidence -> evidence.getSourceType() + ":" + evidence.getSourceId())
                .distinct()
                .limit(3)
                .collect(Collectors.joining(","));
    }

    private List<EntitySource> collectSources(String userId, Set<Long> entityIds, int maxSources) {
        if (maxSources == 0) {
            return List.of();
        }
        Map<String, EntitySource> sources = new LinkedHashMap<>();
        for (Long entityId : entityIds) {
            if (entityEvidenceRepository != null) {
                for (LifeGraphEntityEvidence evidence : safeEntityEvidence(
                        entityEvidenceRepository.findByUserIdAndEntityId(userId, entityId))) {
                    if (StrUtil.isBlank(evidence.getSourceType()) || StrUtil.isBlank(evidence.getSourceId())) {
                        continue;
                    }
                    String sourceType = evidence.getSourceType().trim().toUpperCase(Locale.ROOT);
                    String sourceId = evidence.getSourceId().trim();
                    java.time.LocalDate entryDate = evidence.getEntryDate();
                    if (entryDate == null && evidence.getSourceTime() != null) {
                        entryDate = evidence.getSourceTime().toLocalDate();
                    }
                    sources.putIfAbsent(sourceKey(entityId, sourceType, sourceId),
                            new EntitySource(entityId, sourceType, sourceId, entryDate));
                    if (sources.size() >= maxSources) {
                        return new ArrayList<>(sources.values());
                    }
                }
            }
            for (LifeGraphMention mention : safeMentions(
                    mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(userId, entityId))) {
                if (StrUtil.isBlank(mention.getDiaryId())) {
                    continue;
                }
                sources.putIfAbsent(sourceKey(entityId, "DIARY", mention.getDiaryId()),
                        new EntitySource(entityId, "DIARY", mention.getDiaryId(), mention.getEntryDate()));
                if (sources.size() >= maxSources) {
                    return new ArrayList<>(sources.values());
                }
            }
        }
        return new ArrayList<>(sources.values());
    }

    private String sourceKey(Long entityId, String sourceType, String sourceId) {
        return entityId + "|" + sourceType + "|" + sourceId;
    }

    private boolean isUsableAutomaticEvidence(LifeGraphRelationEvidence evidence) {
        if (evidence == null
                || !AUTOMATIC_SOURCE_TYPES.contains(normalizeSourceType(evidence.getSourceType()))
                || StrUtil.isBlank(evidence.getSourceId())
                || evidence.getOccurrenceCount() == null
                || evidence.getOccurrenceCount() < 1
                || evidence.getConfidence() == null) {
            return false;
        }
        return evidence.getConfidence().doubleValue() >= MIN_EVIDENCE_CONFIDENCE;
    }

    private String normalizeSourceType(String sourceType) {
        return sourceType == null ? "" : sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private List<LifeGraphEntityEvidence> safeEntityEvidence(List<LifeGraphEntityEvidence> values) {
        return values == null ? List.of() : values;
    }

    private String formatProps(String propsJson) {
        if (StrUtil.isBlank(propsJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(propsJson);
            if (!node.isObject()) {
                return null;
            }
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            List<String> emptyKeys = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isNull()
                        || (field.getValue().isTextual() && field.getValue().asText().isEmpty())) {
                    emptyKeys.add(field.getKey());
                }
            }
            emptyKeys.forEach(object::remove);
            return object.isEmpty() ? null : object.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isVisible(LifeGraphEntity entity, LocalDateTime now) {
        return entity != null
                && !Boolean.TRUE.equals(entity.getHidden())
                && (entity.getValidUntil() == null || entity.getValidUntil().isAfter(now));
    }

    private Long physicalSourceId(LifeGraphRelation relation) {
        return relation.getSourceId();
    }

    private Long physicalTargetId(LifeGraphRelation relation) {
        return relation.getTargetId();
    }

    private Long semanticSourceId(LifeGraphRelation relation) {
        return relation.getSemanticSourceId() == null ? relation.getSourceId() : relation.getSemanticSourceId();
    }

    private Long semanticTargetId(LifeGraphRelation relation) {
        return relation.getSemanticTargetId() == null ? relation.getTargetId() : relation.getSemanticTargetId();
    }

    private String relationKey(LifeGraphRelation relation) {
        if (relation.getId() != null) {
            return "id:" + relation.getId();
        }
        return semanticSourceId(relation) + "|" + semanticTargetId(relation) + "|" + relation.getType();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private List<LifeGraphRelationEvidence> safeEvidence(List<LifeGraphRelationEvidence> values) {
        return values == null ? List.of() : values;
    }

    private List<LifeGraphRelation> safeRelations(List<LifeGraphRelation> values) {
        return values == null ? List.of() : values;
    }

    private List<LifeGraphEntity> safeEntities(List<LifeGraphEntity> values) {
        return values == null ? List.of() : values;
    }

    private List<LifeGraphMention> safeMentions(List<LifeGraphMention> values) {
        return values == null ? List.of() : values;
    }

    private record EntitySource(Long entityId, String sourceType, String sourceId,
                                java.time.LocalDate entryDate) {
    }
}
