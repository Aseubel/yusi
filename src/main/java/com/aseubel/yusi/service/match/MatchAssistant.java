package com.aseubel.yusi.service.match;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MatchAssistant {
        @UserMessage("""
                        请为你（用户A的AI知己）的用户撰写一封推荐信，向TA推荐另一位用户（用户B）。

                        用户A（你的用户）的匹配画像：
                        {{userAProfile}}

                        用户B（推荐对象）的匹配画像：
                        {{userBProfile}}

                        已知本次匹配结论：
                        - 共鸣原因：{{reason}}
                        - 时机原因：{{timingReason}}
                        - 破冰建议：{{iceBreaker}}

                        任务：
                        基于以上信息写一封更自然、更像统一Agent判断结果的匿名推荐信。

                        要求：
                        1. 不要提及真实姓名。
                        2. 不要泄露隐私细节。
                        3. 要体现“为什么是这个人”以及“为什么是现在”。
                        4. 允许吸收 iceBreaker 的表达，但不要机械复读。
                        5. 以“向你推荐一位'灵魂伙伴'”开头，120-180字。
                        """)
        String generateRecommendationLetterFromMatchDecision(
                        @V("userAProfile") String userAProfile,
                        @V("userBProfile") String userBProfile,
                        @V("reason") String reason,
                        @V("timingReason") String timingReason,
                        @V("iceBreaker") String iceBreaker);

        @UserMessage("""
                        你是统一 AI Agent 的匹配精排器。请根据目标用户与候选用户的长期结构、稳定偏好、近期状态，
                        判断双方在当前阶段是否值得被推荐给彼此。

                        {{preferenceContext}}

                        目标用户画像：
                        {{userAProfile}}

                        候选用户画像：
                        {{userBProfile}}

                        请严格输出 JSON，不要输出任何额外文字，格式如下：
                        {
                          "resonance": true,
                          "score": 86,
                          "reason": "一句话解释为什么两人有共鸣",
                          "timingReason": "一句话解释为什么是现在",
                          "iceBreaker": "一段用于破冰的推荐语"
                        }

                        约束：
                        1. resonance 为布尔值
                        2. score 为 0-100 整数
                        3. 不能泄露真实姓名与隐私细节
                        4. reason、timingReason、iceBreaker 都必须是中文
                        """)
        String rerankMatch(@V("preferenceContext") String preferenceContext,
                        @V("userAProfile") String userAProfile,
                        @V("userBProfile") String userBProfile);
}
