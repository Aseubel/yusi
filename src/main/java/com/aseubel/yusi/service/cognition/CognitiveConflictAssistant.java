package com.aseubel.yusi.service.cognition;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 认知冲突检测 AI 助手（F11.3）。
 * 比对已有画像与新洞察，判断是否存在语义矛盾。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
public interface CognitiveConflictAssistant {

    // TODO Phase 3: 将此 prompt 迁移至 PromptManager 统一管理
    @UserMessage("""
            你是一个认知一致性检测器。请判断以下"已有认知"与"新观察"之间是否存在语义矛盾。

            已有认知（来自 user-persona / lifeGraph）：
            {{existingBelief}}

            新观察（来自最近的对话或日记洞察）：
            {{newObservation}}

            判断标准：
            1. 如果新观察直接与已有认知相反或明显矛盾 → hasConflict: true
            2. 如果新观察只是补充了新的侧面，不矛盾 → hasConflict: false
            3. 模糊的情况，倾向于 hasConflict: false

            请严格输出 JSON，不要输出任何额外文字：
            {
              "hasConflict": false,
              "description": "一句话描述矛盾之处，供 Agent 在对话中自然地提及（hasConflict为false时填空字符串"
            }
            """)
    String detect(@V("existingBelief") String existingBelief,
                  @V("newObservation") String newObservation);
}
