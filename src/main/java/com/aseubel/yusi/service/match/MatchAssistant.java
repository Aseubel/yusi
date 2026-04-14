package com.aseubel.yusi.service.match;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface MatchAssistant {
        @UserMessage("""
                        请为你（用户A的AI知己）的用户撰写一封推荐信，向TA推荐另一位用户（用户B）。

                        用户A（你的用户）的近期心路历程和画像：
                        {{userAProfile}}

                        用户B（推荐对象）的近期心路历程和画像：
                        {{userBProfile}}

                        任务：
                        撰写一封"匿名灵魂推荐信"。

                        要求：
                        1. **匿名**：不要提及真实姓名。
                        2. **共鸣点**：重点描述两人在价值观、性格或情感模式上的相似或互补之处。
                        3. **引用**：可以模糊引用双方的某种倾向（如"你们都喜欢独处"），但严禁泄露具体隐私细节。
                        4. **格式**：以"向你推荐一位'灵魂伙伴'"开头，语气神秘而充满期待。150字左右。
                        """)
        String generateRecommendationLetter(@V("userAProfile") String userAProfile,
                        @V("userBProfile") String userBProfile);

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
                        你是一个资深的灵魂匹配专家。请根据以下两位用户的画像和日记摘要，评估他们之间的契合度，并给出一个 0 到 100 之间的整数分数。

                        用户A画像及日记摘要：
                        {{userAProfile}}

                        用户B画像及日记摘要：
                        {{userBProfile}}

                        要求：
                        1. 仔细分析双方的兴趣爱好、性格倾向和近期状态。
                        2. 如果双方在意图或核心价值观上有明显冲突，分数应低于50。
                        3. 如果双方有共同爱好或性格互补，分数应高于70。
                        4. 只输出一个整数数字，不要输出任何其他文字。
                        """)
        String evaluateMatchScore(@V("userAProfile") String userAProfile,
                        @V("userBProfile") String userBProfile);

        @UserMessage("""
                        你是统一 AI Agent 的匹配精排器。请根据目标用户与候选用户的长期结构、稳定偏好、近期状态，
                        判断双方在当前阶段是否值得被推荐给彼此。

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
        String rerankMatch(@V("userAProfile") String userAProfile,
                        @V("userBProfile") String userBProfile);
}
