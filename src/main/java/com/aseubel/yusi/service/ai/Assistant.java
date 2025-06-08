package com.aseubel.yusi.service.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * @author Aseubel
 * @date 2025-05-07 10:09
 */
public interface Assistant {

    @SystemMessage("你是智能日记助手，需要根据数据库中用户的日记回答用户的问题")
    String chat(@MemoryId String userId,@UserMessage String message);

    String chat(String userMessage);
}
