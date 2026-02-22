package com.aseubel.yusi.service.diary;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @author Aseubel
 * @date 2025-05-07 10:09
 */
public interface Assistant {

    TokenStream chat(@MemoryId String userId, @UserMessage String message);

    @UserMessage("""
            请为你（用户A的AI知己）的用户撰写一封推荐信，向TA推荐另一位用户（用户B）。
            
            用户A（你的用户）的近期心路历程：
            {{userAProfile}}
            
            用户B（推荐对象）的近期心路历程：
            {{userBProfile}}
            
            任务：
            撰写一封“匿名灵魂推荐信”。
            
            要求：
            1. **匿名**：不要提及真实姓名。
            2. **共鸣点**：重点描述两人在价值观、性格或情感模式上的相似或互补之处。
            3. **引用**：可以模糊引用双方的某种倾向（如“你们都喜欢独处”），但严禁泄露具体隐私细节。
            4. **格式**：以“向你推荐一位‘灵魂伙伴’”开头，语气神秘而充满期待。150字左右。
            """)
    TokenStream generateRecommendationLetter(@V("userAProfile") String userAProfile, @V("userBProfile") String userBProfile);
}
