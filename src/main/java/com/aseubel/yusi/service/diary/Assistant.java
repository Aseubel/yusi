package com.aseubel.yusi.service.diary;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

public interface Assistant {

    TokenStream chat(@MemoryId String userId, @UserMessage String message);

    TokenStream chatWithMessage(@MemoryId String userId, @UserMessage String message,
            @UserMessage List<ImageContent> images);

}