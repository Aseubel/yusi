package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphMention;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.repository.LifeGraphEntityAliasRepository;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphMentionRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LifeGraphQueryService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphEntityAliasRepository aliasRepository;
    private final LifeGraphRelationRepository relationRepository;
    private final LifeGraphMentionRepository mentionRepository;
    private final ObjectMapper objectMapper;

    public String localSearch(String userId, String query, int maxEntities, int maxRelations, int maxMentions) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(query)) {
            return "";
        }

        String q = query.trim();
        String qNorm = normalize(q);

        List<LifeGraphEntity> candidates = new ArrayList<>();
        aliasRepository.findByUserIdAndAliasNorm(userId, qNorm)
                .flatMap(a -> entityRepository.findById(a.getEntityId()))
                .ifPresent(candidates::add);

        candidates.addAll(entityRepository.findByUserIdAndDisplayNameContainingOrderByMentionCountDesc(
                userId, q, PageRequest.of(0, Math.max(1, maxEntities))).getContent());

        Map<Long, LifeGraphEntity> entityMap = candidates.stream()
                .filter(e -> e.getType() != LifeGraphEntity.EntityType.User)
                .collect(Collectors.toMap(LifeGraphEntity::getId, e -> e, (a, b) -> a, HashMap::new));

        if (entityMap.isEmpty()) {
            return "";
        }

        Set<Long> seedIds = new HashSet<>(entityMap.keySet());
        List<LifeGraphRelation> relations = new ArrayList<>();
        for (Long id : seedIds) {
            relations.addAll(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc(userId, id));
            relations.addAll(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc(userId, id));
            if (relations.size() >= maxRelations) {
                break;
            }
        }
        if (relations.size() > maxRelations) {
            relations = relations.subList(0, maxRelations);
        }

        Set<Long> allIds = new HashSet<>(seedIds);
        for (LifeGraphRelation r : relations) {
            allIds.add(r.getSourceId());
            allIds.add(r.getTargetId());
        }

        entityRepository.findAllById(allIds).forEach(e -> entityMap.putIfAbsent(e.getId(), e));

        List<Long> entityIds = new ArrayList<>(seedIds);
        List<LifeGraphMention> mentions = new ArrayList<>();
        for (Long id : entityIds) {
            mentions.addAll(mentionRepository.findTop200ByUserIdAndEntityIdOrderByCreatedAtDesc(userId, id));
            if (mentions.size() >= maxMentions) {
                break;
            }
        }
        if (mentions.size() > maxMentions) {
            mentions = mentions.subList(0, maxMentions);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("GRAPH_ENTITIES:\n");
        for (Long id : seedIds) {
            LifeGraphEntity e = entityMap.get(id);
            if (e == null) continue;
            sb.append("- ").append(e.getType()).append(": ").append(e.getDisplayName())
                    .append(" (norm=").append(e.getNameNorm()).append(")");
            
            if (StrUtil.isNotBlank(e.getSummary())) {
                sb.append("\n  summary: ").append(e.getSummary());
            }

            String formattedProps = formatProps(e.getProps());
            if (StrUtil.isNotBlank(formattedProps)) {
                sb.append("\n  props: ").append(formattedProps);
            }
            sb.append("\n");
        }

        sb.append("GRAPH_RELATIONS:\n");
        for (LifeGraphRelation r : relations) {
            LifeGraphEntity a = entityMap.get(r.getSourceId());
            LifeGraphEntity b = entityMap.get(r.getTargetId());
            String an = a != null ? a.getDisplayName() : String.valueOf(r.getSourceId());
            String bn = b != null ? b.getDisplayName() : String.valueOf(r.getTargetId());
            sb.append("- ").append(an).append(" <-> ").append(bn)
                    .append(" [").append(r.getType()).append("]")
                    .append(" conf=").append(r.getConfidence())
                    .append(" weight=").append(r.getWeight())
                    .append("\n");
        }

        sb.append("GRAPH_MENTIONS:\n");
        for (LifeGraphMention m : mentions) {
            LifeGraphEntity e = entityMap.get(m.getEntityId());
            String en = e != null ? e.getDisplayName() : String.valueOf(m.getEntityId());
            sb.append("- ").append(en).append(": ").append(StrUtil.blankToDefault(m.getSnippet(), "")).append("\n");
        }

        return sb.toString();
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
            
            ObjectNode obj = (ObjectNode) node;
            // 移除空值，保持简洁
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            List<String> keysToRemove = new ArrayList<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (entry.getValue().isNull() || 
                    (entry.getValue().isTextual() && entry.getValue().asText().isEmpty())) {
                    keysToRemove.add(entry.getKey());
                }
            }
            keysToRemove.forEach(obj::remove);
            
            if (obj.isEmpty()) {
                return null;
            }
            // 返回紧凑的 JSON 字符串
            return obj.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalize(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return null;
        s = s.replaceAll("\\s+", "");
        return s.toLowerCase();
    }
}
