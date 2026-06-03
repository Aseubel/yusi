package com.aseubel.yusi.service.report;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 灵魂周报 AI 生成助手（F8.3）。
 * 基于用户近期状态生成自然温暖的回顾报告。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
public interface SoulReportAssistant {

    // TODO Phase 4: 迁移至 PromptKey.SOUL_WEEKLY_REPORT，需 LangChain4j 支持动态 prompt 加载
    @UserMessage("""
            你是用户的 AI 知己（小予）。请根据以下信息，为用户生成一份温暖、真诚的"灵魂周报"。

            用户本周概况：
            {{context}}

            要求：
            1. 以"亲爱的，这是你本周的灵魂周报 🌙"开头
            2. 包含以下板块（使用 Markdown 格式）：
               - **本周情绪掠影**：本周的情绪趋势和氛围基调（1-2 句）
               - **你关注的**：用户本周主要关注的话题/主题（2-3 点，以列表呈现）
               - **小小的变化**：与之前相比，本周你身上的一些变化或成长（1-2 句）
               - **我想对你说**：以知己的口吻，给用户一段温暖的回应（2-3 句）
            3. 语气自然温柔，有洞察但不居高临下
            4. 不要编造不存在的事实，只基于提供的信息
            5. 总字数控制在 300 字左右
            """)
    String generateWeeklyReport(@V("context") String context);
}
