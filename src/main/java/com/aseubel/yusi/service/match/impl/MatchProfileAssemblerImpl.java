package com.aseubel.yusi.service.match.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.MatchProfile;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.repository.MatchProfileRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.aseubel.yusi.service.user.UserService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchProfileAssemblerImpl implements MatchProfileAssembler {

    private static final String MATCH_PROFILE_COLLECTION = "yusi_match_profile";

    private final LifeGraphEntityRepository lifeGraphEntityRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final MatchProfileRepository matchProfileRepository;
    private final UserPersonaService userPersonaService;
    private final UserService userService;
    private final MilvusClientV2 milvusClientV2;
    private final EmbeddingModel embeddingModel;

    @Override
    @Transactional
    public MatchProfile refreshProfile(String userId) {
        User user = userService.getUserByUserId(userId);
        String lifeGraphSummary = buildLifeGraphSummary(userId);
        String personaSummary = buildPersonaSummary(userId);
        String midMemorySummary = buildMidMemorySummary(userId);
        String matchIntentSummary = buildMatchIntentSummary(user);
        String agentDecisionFocus = buildAgentDecisionFocus(lifeGraphSummary, personaSummary, midMemorySummary, user);

        String profileText = """
                匹配意图：
                %s

                Agent判断重点：
                %s

                长期结构（高权重）：
                %s

                稳定偏好（中高权重）：
                %s

                当前阶段（辅助权重）：
                %s
                """.formatted(
                matchIntentSummary,
                agentDecisionFocus,
                lifeGraphSummary,
                personaSummary,
                midMemorySummary).trim();

        MatchProfile profile = matchProfileRepository.findByUserId(userId)
                .orElseGet(() -> MatchProfile.builder().userId(userId).version(0L).build());

        profile.setLifeGraphSummary(lifeGraphSummary);
        profile.setPersonaSummary(personaSummary);
        profile.setMidMemorySummary(midMemorySummary);
        profile.setProfileText(profileText);
        profile.setVersion((profile.getVersion() == null ? 0L : profile.getVersion()) + 1);
        profile.setUpdatedAt(LocalDateTime.now());

        MatchProfile saved = matchProfileRepository.save(profile);
        syncToMilvus(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public MatchProfile ensureProfile(String userId) {
        return matchProfileRepository.findByUserId(userId)
                .filter(p -> StrUtil.isNotBlank(p.getProfileText()))
                .orElseGet(() -> refreshProfile(userId));
    }

    private String buildLifeGraphSummary(String userId) {
        List<LifeGraphEntity> entities = lifeGraphEntityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        if (entities == null || entities.isEmpty()) {
            return "长期结构信息较少。";
        }
        return entities.stream()
                .filter(entity -> entity.getType() != LifeGraphEntity.EntityType.User)
                .sorted(Comparator
                        .comparing((LifeGraphEntity entity) -> lifeGraphPriority(entity.getType())).reversed()
                        .thenComparing(LifeGraphEntity::getMentionCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(entity -> {
                    String summary = StrUtil.blankToDefault(entity.getSummary(), "暂无结构化摘要");
                    return "- [%s] %s：%s".formatted(toChineseType(entity.getType()), entity.getDisplayName(), summary);
                })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("长期结构信息较少。");
    }

    private String buildPersonaSummary(String userId) {
        UserPersona persona = userPersonaService.getUserPersona(userId);
        if (persona == null) {
            return "稳定偏好信息较少。";
        }
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(persona.getPreferredName())) {
            parts.add("自我称呼偏好: " + persona.getPreferredName());
        }
        if (StrUtil.isNotBlank(persona.getLocation())) {
            parts.add("稳定地点线索: " + persona.getLocation());
        }
        if (StrUtil.isNotBlank(persona.getInterests())) {
            parts.add("兴趣偏好: " + persona.getInterests());
        }
        if (StrUtil.isNotBlank(persona.getTone())) {
            parts.add("偏好语气: " + persona.getTone());
        }
        if (StrUtil.isNotBlank(persona.getCustomInstructions())) {
            parts.add("相处方式: " + persona.getCustomInstructions());
        }
        return parts.isEmpty() ? "稳定偏好信息较少。" : String.join("\n", parts);
    }

    private String buildMidMemorySummary(String userId) {
        List<MidTermMemory> memories = midTermMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(0, 6));
        if (memories == null || memories.isEmpty()) {
            return "近期状态信息较少。";
        }
        return memories.stream()
                .sorted(Comparator
                        .comparing(MidTermMemory::getImportance, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MidTermMemory::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(memory -> "- " + truncate(memory.getSummary(), 120))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("近期状态信息较少。");
    }

    private String buildMatchIntentSummary(User user) {
        if (user == null || StrUtil.isBlank(user.getMatchIntent())) {
            return "当前未显式声明匹配目标，以自然共鸣与长期关系潜力为主。";
        }
        return "用户当前的匹配意图是：" + user.getMatchIntent().trim();
    }

    private String buildAgentDecisionFocus(String lifeGraphSummary, String personaSummary,
            String midMemorySummary, User user) {
        List<String> focuses = new ArrayList<>();
        if (user != null && StrUtil.isNotBlank(user.getMatchIntent())) {
            focuses.add("优先尊重用户当前明确的匹配意图");
        }
        if (!"长期结构信息较少。".equals(lifeGraphSummary)) {
            focuses.add("优先判断长期人生主题和关系模式是否相容");
        }
        if (!"稳定偏好信息较少。".equals(personaSummary)) {
            focuses.add("关注相处节奏、语气偏好与边界感是否兼容");
        }
        if (!"近期状态信息较少。".equals(midMemorySummary)) {
            focuses.add("把当前阶段状态作为时机修正项，而不是主导项");
        }
        if (focuses.isEmpty()) {
            return "认知信息有限，暂以谨慎召回和保守精排为主。";
        }
        return focuses.stream().collect(Collectors.joining("；")) + "。";
    }

    private int lifeGraphPriority(LifeGraphEntity.EntityType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case Topic -> 5;
            case Event -> 4;
            case Person -> 4;
            case Emotion -> 3;
            case Place -> 2;
            case Item -> 1;
            case User -> 0;
        };
    }

    private String toChineseType(LifeGraphEntity.EntityType type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case Person -> "人物";
            case Event -> "事件";
            case Place -> "地点";
            case Emotion -> "情绪";
            case Topic -> "主题";
            case Item -> "物品";
            case User -> "用户";
        };
    }

    private String truncate(String text, int maxLength) {
        if (StrUtil.isBlank(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void syncToMilvus(MatchProfile profile) {
        if (profile == null || StrUtil.isBlank(profile.getProfileText())) {
            return;
        }
        try {
            milvusClientV2.delete(DeleteReq.builder()
                    .collectionName(MATCH_PROFILE_COLLECTION)
                    .filter("id == '" + profile.getUserId() + "'")
                    .build());
        } catch (Exception e) {
            log.debug("删除旧匹配画像失败，将继续尝试写入: userId={}", profile.getUserId(), e);
        }

        JsonObject metadata = new JsonObject();
        metadata.addProperty("userId", profile.getUserId());
        metadata.addProperty("version", String.valueOf(profile.getVersion()));
        metadata.addProperty("updatedAt", String.valueOf(profile.getUpdatedAt()));

        JsonObject row = new JsonObject();
        row.addProperty("id", profile.getUserId());
        row.addProperty("text", profile.getProfileText());

        JsonArray vectorArray = new JsonArray();
        for (float v : embeddingModel.embed(profile.getProfileText()).content().vector()) {
            vectorArray.add(v);
        }
        row.add("vector", vectorArray);
        row.add("metadata", metadata);

        InsertReq insertReq = InsertReq.builder()
                .collectionName(MATCH_PROFILE_COLLECTION)
                .data(List.of(row))
                .build();
        milvusClientV2.insert(insertReq);
    }
}
