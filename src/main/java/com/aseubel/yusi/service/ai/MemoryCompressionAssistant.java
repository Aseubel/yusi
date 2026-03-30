package com.aseubel.yusi.service.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public interface MemoryCompressionAssistant {

    String extractMemory(@MemoryId String memoryId, @UserMessage String prompt);
}
