package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.redis.IRedisService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * @author Aseubel
 * @date 2025-05-07 10:08
 */
@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final IRedisService redissonService;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redissonService.getValue("langchain:" + memoryId.toString());
        return messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = messagesToJson(messages);
        redissonService.setValue("langchain:" + memoryId.toString(), json);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redissonService.remove("langchain:" + memoryId.toString());
    }
}
