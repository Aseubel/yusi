package com.aseubel.yusi.pojo.dto.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 成长可见化响应 DTO（F8.5）。
 * 展示 Agent 对用户的了解程度：实体数、画像完整度、记忆量、互动量、综合指数。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentGrowthResponse {

    /** 综合了解指数 0-100 */
    private Integer understandingIndex;

    /** 人生图谱实体总数 */
    private Long lifeGraphEntityCount;

    /** 各类实体数量: {Person: 5, Event: 3, ...} */
    private Map<String, Long> lifeGraphBreakdown;

    /** 用户画像完整度百分比 0-100 */
    private Integer personaCompleteness;

    /** 有效中期记忆洞察数 */
    private Long midMemoryInsightCount;

    /** 日记总数 */
    private Long diaryCount;

    /** AI 对话总轮数 */
    private Long chatTurnCount;

    /** 陪伴天数（从第一天的日记或对话算起） */
    private Long companionDays;

    /** 一段自然的描述文案 */
    private String description;
}
