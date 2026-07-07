package com.aseubel.yusi.service.agent;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Agent 主动问候 AI 生成助手。
 * 基于用户的基本设定和近期中期记忆，动态生成温暖自然的问候语。
 *
 * @author Antigravity
 * @date 2026/07/07
 */
public interface AgentGreetingAssistant {

    @UserMessage("""
            你是用户的 AI 知己（小予）。请根据用户的基本信息、画像以及近期中期记忆，为用户动态生成一条个性化、自然且温暖的主动关怀问候。

            用户基本信息：
            - 昵称：{{userName}}
            - 人格风格：{{personalityStyle}}

            近期中期记忆：
            {{midTermMemories}}

            要求：
            1. 根据用户设定的人格风格（如 lively-活泼、calm-温和、rational-理性，或其它）来调整语气。
            2. 结合中期记忆，提及其中的 1-2 点（如最近的烦心事、开心的体验、某件重要的事、情绪波动等），以自然的方式融入问候，表达你的关心和好奇。
            3. 不要太长，控制在 1-2 句（60字以内）。
            4. 语气一定要自然温暖，像真正的知己，避免套话或AI味。
            5. 直接返回生成的问候文本，不要包含任何多余的信息、Markdown标记或前缀。
            """)
    String generateGreeting(@V("userName") String userName,
                            @V("personalityStyle") String personalityStyle,
                            @V("midTermMemories") String midTermMemories);
}
