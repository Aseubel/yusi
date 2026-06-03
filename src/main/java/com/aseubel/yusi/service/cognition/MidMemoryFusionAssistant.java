package com.aseubel.yusi.service.cognition;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 跨源记忆融合 AI 助手（F11.4）。
 * 判断两条 mid-memory 洞察是否指向同一主题，若是则生成合并后的摘要。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
public interface MidMemoryFusionAssistant {

    // TODO Phase 3: 迁移 prompt 至 PromptManager
    @UserMessage("""
            你是记忆融合器。判断以下两条用户洞察是否指向同一主题或同一件事情。

            洞察 A：
            {{insightA}}

            洞察 B：
            {{insightB}}

            标准：
            1. 如果两条洞察明显是同一件事/同一主题/同一情绪变化 → shouldMerge: true
            2. 如果只是有弱关联但不是同一件事 → shouldMerge: false
            3. 模糊情况倾向 false

            请严格输出 JSON，不要输出任何额外文字：
            {
              "shouldMerge": false,
              "mergedSummary": "若shouldMerge为true，输出合并后的一句话摘要；否则为空字符串"
            }
            """)
    String evaluate(@V("insightA") String insightA, @V("insightB") String insightB);
}
