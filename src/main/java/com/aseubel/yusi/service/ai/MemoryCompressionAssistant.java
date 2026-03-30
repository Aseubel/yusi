package com.aseubel.yusi.service.ai;

import dev.langchain4j.service.UserMessage;

public interface MemoryCompressionAssistant {

    String extractMemory(@UserMessage String prompt);
}
