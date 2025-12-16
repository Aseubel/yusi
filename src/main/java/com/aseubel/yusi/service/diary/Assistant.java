package com.aseubel.yusi.service.diary;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * @author Aseubel
 * @date 2025-05-07 10:09
 */
public interface Assistant {

    TokenStream chat(@MemoryId String userId, @UserMessage String message);

    @UserMessage("""
            用户刚写了一篇日记。
            
            日记内容：
            {{diaryContent}}
            
            任务：
            请阅读日记内容，并给出一个简短的回应（100字以内）。
            
            回应要求：
            1. **共情**：首先确认并验证用户的情绪。
            2. **理解**：体现你读懂了TA的内心想法，而不仅仅是复述事情。
            3. **提问**（可选）：如果合适，可以温柔地问一个问题，引导TA更深层地探索。
            4. **语气**：像老朋友一样自然、温暖，不要说教。
            """)
    String generateDiaryResponse(@UserMessage("diaryContent") String diaryContent);
}
