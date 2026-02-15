package com.aseubel.yusi.service.lifegraph.impl;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.LifeGraphRelationRepository;
import com.aseubel.yusi.service.lifegraph.CommunityInsightService;
import com.aseubel.yusi.service.lifegraph.dto.CommunityInsight;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityInsightServiceImpl implements CommunityInsightService {

    private final LifeGraphEntityRepository entityRepository;
    private final LifeGraphRelationRepository relationRepository;

    private static final int MIN_COMMUNITY_SIZE = 3;
    private static final int MAX_COMMUNITIES = 10;

    @Override
    public List<CommunityInsight> detectCommunities(String userId) {
        List<LifeGraphEntity> entities = entityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        if (entities.size() < MIN_COMMUNITY_SIZE) {
            return Collections.emptyList();
        }

        Map<Long, LifeGraphEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(LifeGraphEntity::getId, e -> e));

        List<LifeGraphRelation> allRelations = new ArrayList<>();
        for (LifeGraphEntity entity : entities) {
            allRelations.addAll(relationRepository.findTop200ByUserIdAndSourceIdOrderByUpdatedAtDesc(userId, entity.getId()));
            allRelations.addAll(relationRepository.findTop200ByUserIdAndTargetIdOrderByUpdatedAtDesc(userId, entity.getId()));
        }

        Map<Long, Set<Long>> adjacencyList = buildAdjacencyList(allRelations, entityMap.keySet());

        List<Set<Long>> connectedComponents = findConnectedComponents(adjacencyList, entityMap.keySet());

        List<CommunityInsight> communities = new ArrayList<>();
        int communityIndex = 0;
        
        for (Set<Long> component : connectedComponents) {
            if (component.size() < MIN_COMMUNITY_SIZE) {
                continue;
            }
            if (communities.size() >= MAX_COMMUNITIES) {
                break;
            }

            CommunityInsight insight = buildCommunityInsight(
                    userId,
                    component,
                    entityMap,
                    allRelations,
                    communityIndex++
            );
            communities.add(insight);
        }

        communities.sort((a, b) -> Double.compare(b.getCohesion(), a.getCohesion()));
        return communities;
    }

    @Override
    public CommunityInsight getCommunityDetail(String userId, String communityId) {
        List<CommunityInsight> communities = detectCommunities(userId);
        return communities.stream()
                .filter(c -> c.getCommunityId().equals(communityId))
                .findFirst()
                .orElse(null);
    }

    private Map<Long, Set<Long>> buildAdjacencyList(List<LifeGraphRelation> relations, Set<Long> entityIds) {
        Map<Long, Set<Long>> adjacencyList = new HashMap<>();
        
        for (Long entityId : entityIds) {
            adjacencyList.put(entityId, new HashSet<>());
        }

        for (LifeGraphRelation relation : relations) {
            Long source = relation.getSourceId();
            Long target = relation.getTargetId();
            
            if (entityIds.contains(source) && entityIds.contains(target)) {
                adjacencyList.computeIfAbsent(source, k -> new HashSet<>()).add(target);
                adjacencyList.computeIfAbsent(target, k -> new HashSet<>()).add(source);
            }
        }

        return adjacencyList;
    }

    private List<Set<Long>> findConnectedComponents(Map<Long, Set<Long>> adjacencyList, Set<Long> entityIds) {
        List<Set<Long>> components = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        for (Long entityId : entityIds) {
            if (!visited.contains(entityId)) {
                Set<Long> component = new HashSet<>();
                dfs(entityId, adjacencyList, visited, component);
                components.add(component);
            }
        }

        components.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return components;
    }

    private void dfs(Long nodeId, Map<Long, Set<Long>> adjacencyList, Set<Long> visited, Set<Long> component) {
        visited.add(nodeId);
        component.add(nodeId);

        Set<Long> neighbors = adjacencyList.getOrDefault(nodeId, Collections.emptySet());
        for (Long neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, adjacencyList, visited, component);
            }
        }
    }

    private CommunityInsight buildCommunityInsight(
            String userId,
            Set<Long> component,
            Map<Long, LifeGraphEntity> entityMap,
            List<LifeGraphRelation> allRelations,
            int index
    ) {
        List<LifeGraphEntity> communityEntities = component.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.getMentionCount(), a.getMentionCount()))
                .collect(Collectors.toList());

        Map<Long, Double> centralityScores = calculateCentrality(component, allRelations);

        List<CommunityInsight.EntitySummary> entitySummaries = communityEntities.stream()
                .limit(10)
                .map(entity -> CommunityInsight.EntitySummary.builder()
                        .entityId(entity.getId())
                        .displayName(entity.getDisplayName())
                        .entityType(entity.getType().name())
                        .mentionCount(entity.getMentionCount())
                        .centralityScore(centralityScores.getOrDefault(entity.getId(), 0.0))
                        .build())
                .collect(Collectors.toList());

        CommunityInsight.CommunityType type = inferCommunityType(communityEntities);

        int relationCount = countInternalRelations(component, allRelations);

        double cohesion = calculateCohesion(component, allRelations, relationCount);

        LocalDate firstActive = communityEntities.stream()
                .map(LifeGraphEntity::getFirstMentionDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);

        LocalDate lastActive = communityEntities.stream()
                .map(LifeGraphEntity::getLastMentionAt)
                .filter(Objects::nonNull)
                .map(dt -> dt.toLocalDate())
                .max(LocalDate::compareTo)
                .orElse(null);

        String description = generateCommunityDescription(type, communityEntities);

        return CommunityInsight.builder()
                .communityId("community_" + index)
                .communityName(generateCommunityName(type, communityEntities))
                .type(type)
                .description(description)
                .entities(entitySummaries)
                .entityCount(component.size())
                .relationCount(relationCount)
                .cohesion(cohesion)
                .firstActiveDate(firstActive)
                .lastActiveDate(lastActive)
                .build();
    }

    private Map<Long, Double> calculateCentrality(Set<Long> component, List<LifeGraphRelation> allRelations) {
        Map<Long, Integer> degreeCount = new HashMap<>();
        
        for (Long entityId : component) {
            degreeCount.put(entityId, 0);
        }

        for (LifeGraphRelation relation : allRelations) {
            if (component.contains(relation.getSourceId())) {
                degreeCount.merge(relation.getSourceId(), 1, Integer::sum);
            }
            if (component.contains(relation.getTargetId())) {
                degreeCount.merge(relation.getTargetId(), 1, Integer::sum);
            }
        }

        int maxDegree = degreeCount.values().stream().max(Integer::compare).orElse(1);
        
        return degreeCount.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (double) e.getValue() / maxDegree
                ));
    }

    private int countInternalRelations(Set<Long> component, List<LifeGraphRelation> allRelations) {
        int count = 0;
        for (LifeGraphRelation relation : allRelations) {
            if (component.contains(relation.getSourceId()) && component.contains(relation.getTargetId())) {
                count++;
            }
        }
        return count;
    }

    private double calculateCohesion(Set<Long> component, List<LifeGraphRelation> allRelations, int internalRelations) {
        int n = component.size();
        if (n <= 1) return 0.0;

        int maxPossibleRelations = n * (n - 1) / 2;
        return (double) internalRelations / maxPossibleRelations;
    }

    private CommunityInsight.CommunityType inferCommunityType(List<LifeGraphEntity> entities) {
        Map<LifeGraphEntity.EntityType, Long> typeCounts = entities.stream()
                .collect(Collectors.groupingBy(LifeGraphEntity::getType, Collectors.counting()));

        List<LifeGraphEntity.EntityType> topTypes = typeCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topTypes.isEmpty()) {
            return CommunityInsight.CommunityType.OTHER;
        }

        LifeGraphEntity.EntityType dominantType = topTypes.get(0);
        
        boolean hasPlace = topTypes.contains(LifeGraphEntity.EntityType.Place);
        boolean hasPerson = topTypes.contains(LifeGraphEntity.EntityType.Person);
        boolean hasEvent = topTypes.contains(LifeGraphEntity.EntityType.Event);

        if (dominantType == LifeGraphEntity.EntityType.Person && hasPlace) {
            return CommunityInsight.CommunityType.FRIENDS;
        }
        
        if (hasEvent && hasPlace) {
            return CommunityInsight.CommunityType.WORK;
        }

        if (dominantType == LifeGraphEntity.EntityType.Person) {
            return CommunityInsight.CommunityType.FAMILY;
        }

        if (dominantType == LifeGraphEntity.EntityType.Topic || dominantType == LifeGraphEntity.EntityType.Item) {
            return CommunityInsight.CommunityType.HOBBY;
        }

        return CommunityInsight.CommunityType.OTHER;
    }

    private String generateCommunityName(CommunityInsight.CommunityType type, List<LifeGraphEntity> entities) {
        String typeLabel = switch (type) {
            case WORK -> "工作圈";
            case FAMILY -> "家庭圈";
            case FRIENDS -> "朋友圈";
            case HOBBY -> "兴趣圈";
            case OTHER -> "生活圈";
        };

        String topEntity = entities.stream()
                .filter(e -> e.getType() == LifeGraphEntity.EntityType.Person ||
                           e.getType() == LifeGraphEntity.EntityType.Place)
                .findFirst()
                .map(LifeGraphEntity::getDisplayName)
                .orElse("");

        if (!topEntity.isEmpty()) {
            return typeLabel + " · " + topEntity;
        }
        return typeLabel;
    }

    private String generateCommunityDescription(CommunityInsight.CommunityType type, List<LifeGraphEntity> entities) {
        long personCount = entities.stream().filter(e -> e.getType() == LifeGraphEntity.EntityType.Person).count();
        long placeCount = entities.stream().filter(e -> e.getType() == LifeGraphEntity.EntityType.Place).count();
        long eventCount = entities.stream().filter(e -> e.getType() == LifeGraphEntity.EntityType.Event).count();

        StringBuilder sb = new StringBuilder();
        sb.append("包含 ");
        if (personCount > 0) sb.append(personCount).append(" 位人物");
        if (placeCount > 0) sb.append("、").append(placeCount).append(" 个地点");
        if (eventCount > 0) sb.append("、").append(eventCount).append(" 个事件");

        return sb.toString();
    }
}
