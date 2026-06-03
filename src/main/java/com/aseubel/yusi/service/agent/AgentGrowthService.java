package com.aseubel.yusi.service.agent;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.dto.agent.AgentGrowthResponse;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 成长可见化服务（F8.5）。
 * 聚合 lifeGraph、persona、mid-memory、diary、chat 数据，生成了解指数。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentGrowthService {

    private final LifeGraphEntityRepository lifeGraphRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final DiaryRepository diaryRepository;
    private final ChatMemoryMessageRepository chatMessageRepository;
    private final AgentPersonaConfigRepository personaConfigRepository;
    private final com.aseubel.yusi.service.user.UserPersonaService userPersonaService;

    public AgentGrowthResponse getGrowth(String userId) {
        // 1. 人生图谱
        long entityCount = lifeGraphRepository.countByUserId(userId);
        Map<String, Long> breakdown = new LinkedHashMap<>();
        breakdown.put("人物", lifeGraphRepository
                .findByUserIdAndType(userId, com.aseubel.yusi.pojo.entity.LifeGraphEntity.EntityType.Person)
                .size() + (long) 0);
        breakdown.put("事件", lifeGraphRepository
                .findByUserIdAndType(userId, com.aseubel.yusi.pojo.entity.LifeGraphEntity.EntityType.Event)
                .size() + (long) 0);
        breakdown.put("地点", lifeGraphRepository
                .findByUserIdAndType(userId, com.aseubel.yusi.pojo.entity.LifeGraphEntity.EntityType.Place)
                .size() + (long) 0);
        breakdown.put("情绪", lifeGraphRepository
                .findByUserIdAndType(userId, com.aseubel.yusi.pojo.entity.LifeGraphEntity.EntityType.Emotion)
                .size() + (long) 0);
        breakdown.put("主题", lifeGraphRepository
                .findByUserIdAndType(userId, com.aseubel.yusi.pojo.entity.LifeGraphEntity.EntityType.Topic)
                .size() + (long) 0);

        // 2. 画像完整度
        int personaScore = calcPersonaCompleteness(userId);

        // 3. 有效中期记忆
        List<MidTermMemory> validMemories = midTermMemoryRepository
                .findValidByUserId(userId, LocalDateTime.now(), PageRequest.of(0, 100));
        long memoryCount = (long) validMemories.size();

        // 4. 日记
        long diaryCount = diaryRepository.countByUserId(userId);

        // 5. 对话
        long chatTurns = chatMessageRepository.countByMemoryId(userId);

        // 6. 陪伴天数
        long companionDays = calcCompanionDays(userId);

        // 7. 综合指数
        int index = calcUnderstandingIndex(entityCount, personaScore, memoryCount,
                diaryCount, chatTurns, companionDays);

        // 8. 描述
        String description = buildDescription(index, entityCount, diaryCount, chatTurns, companionDays);

        return AgentGrowthResponse.builder()
                .understandingIndex(index)
                .lifeGraphEntityCount(entityCount)
                .lifeGraphBreakdown(breakdown)
                .personaCompleteness(personaScore)
                .midMemoryInsightCount(memoryCount)
                .diaryCount(diaryCount)
                .chatTurnCount(chatTurns)
                .companionDays(companionDays)
                .description(description)
                .build();
    }

    private int calcPersonaCompleteness(String userId) {
        UserPersona persona = userPersonaService.getUserPersona(userId);
        if (persona == null) {
            return 0;
        }
        int filled = 0;
        int total = 5;
        if (StrUtil.isNotBlank(persona.getPreferredName())) { filled++; }
        if (StrUtil.isNotBlank(persona.getInterests())) { filled++; }
        if (StrUtil.isNotBlank(persona.getTone())) { filled++; }
        if (StrUtil.isNotBlank(persona.getLocation())) { filled++; }
        if (StrUtil.isNotBlank(persona.getCustomInstructions())) { filled++; }
        return Math.min(100, filled * 100 / total);
    }

    private long calcCompanionDays(String userId) {
        try {
            java.util.Optional<com.aseubel.yusi.pojo.entity.ChatMemoryMessage> firstMsg =
                    chatMessageRepository.findByMemoryIdOrderByCreatedAtAsc(userId)
                            .stream().findFirst();
            if (firstMsg.isPresent() && firstMsg.get().getCreatedAt() != null) {
                return ChronoUnit.DAYS.between(firstMsg.get().getCreatedAt().toLocalDate(), LocalDate.now()) + 1;
            }
        } catch (Exception e) {
            log.debug("计算陪伴天数失败: userId={}", userId, e);
        }
        return 0L;
    }

    /**
     * 综合了解指数：各维度加权求和，满分 100。
     * 权重设计：
     * - 日记(最核心的认知来源) 30%
     * - 对话轮数 25%
     * - 画像完整度 20%
     * - 中期记忆洞察 15%
     * - 生命图谱实体 10%
     */
    private int calcUnderstandingIndex(long entities, int personaScore, long memories,
            long diaries, long chats, long days) {
        // 每个维度归一化到 0-100
        int diaryScore = (int) Math.min(100, diaries * 10);        // 10篇日记=满分
        int chatScore = (int) Math.min(100, chats / 2);            // 200条对话=满分
        int memoryScore = (int) Math.min(100, memories * 20);      // 5条洞察=满分
        int entityScore = (int) Math.min(100, entities * 10);      // 10个实体=满分

        return (int) Math.min(100,
                diaryScore * 0.30
                + chatScore * 0.25
                + personaScore * 0.20
                + memoryScore * 0.15
                + entityScore * 0.10);
    }

    private String buildDescription(int index, long entities, long diaries,
            long chats, long days) {
        if (index < 20) {
            return "小予刚刚开始认识你。多写几篇日记，和 AI 多聊聊，让我慢慢了解你吧~";
        } else if (index < 45) {
            return "小予正在逐渐了解你。你的兴趣、偏好和日常节奏，我已经有了一些印象。";
        } else if (index < 70) {
            return "小予已经比较熟悉你了。我知道你喜欢什么、在乎什么，也了解你当前的状态。";
        } else if (index < 90) {
            return "我们已经建立了很深的默契。你的情绪变化、人生主题、关系模式，我都有比较清晰的感知。";
        } else {
            return "你是小予最熟悉的人之一。我可以很自然地感知你的状态变化，陪你度过每一个阶段。";
        }
    }
}
