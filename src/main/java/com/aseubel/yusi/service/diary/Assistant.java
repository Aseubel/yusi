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
}
