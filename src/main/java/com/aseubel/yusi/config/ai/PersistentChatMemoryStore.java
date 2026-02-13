package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.redis.service.IRedisService;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * 持久化聊天记忆存储 (MySQL + Redis)
 * 
 * 采用 Read-Through / Write-Through 策略：
 * 1. 读取时优先查 Redis，未命中则查 DB 并回填 Redis。
 * 2. 写入时同时更新 DB 和 Redis。
 * 
 * @author Aseubel
 * @date 2026/02/10
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMessageRepository messageRepository;
    private final IRedisService redisService;

    private static final int MAX_LOAD_MESSAGES = 20;
    private static final long REDIS_TTL_MS = 30 * 60 * 1000; // 30 minutes

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Object memoryId) {
        String memId = memoryId.toString();
        String cacheKey = getCacheKey(memId);
        
        String json = redisService.getValue(cacheKey);
        if (json != null) {
            try {
                return messagesFromJson(json);
            } catch (Exception e) {
                log.warn("Failed to parse chat memory from Redis: {}", e.getMessage());
            }
        }
        
        List<ChatMemoryMessage> entities = messageRepository.findByMemoryIdOrderByCreatedAtDesc(
                memId, PageRequest.of(0, MAX_LOAD_MESSAGES));
        
        if (entities.isEmpty()) {
            return new ArrayList<>();
        }

        Collections.reverse(entities);

        List<ChatMessage> messages = entities.stream()
                .map(this::toChatMessage)
                .collect(Collectors.toList());

        redisService.setValue(cacheKey, messagesToJson(messages), REDIS_TTL_MS);
        
        return messages;
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String memId = memoryId.toString();
        String cacheKey = getCacheKey(memId);
        
        redisService.setValue(cacheKey, messagesToJson(messages), REDIS_TTL_MS);

        if (messages == null || messages.isEmpty()) return;

        ChatMessage lastMsg = messages.get(messages.size() - 1);
        String newContent = extractText(lastMsg);
        
        List<ChatMemoryMessage> lastDbMsgs = messageRepository.findByMemoryIdOrderByCreatedAtDesc(
                memId, PageRequest.of(0, 1));
        
        boolean shouldInsert = true;
        if (!lastDbMsgs.isEmpty()) {
            ChatMemoryMessage lastDb = lastDbMsgs.get(0);
            if (lastDb.getContent().equals(newContent)) {
                shouldInsert = false;
            }
        }
        
        if (shouldInsert) {
             ChatMemoryMessage entity = ChatMemoryMessage.builder()
                    .memoryId(memId)
                    .role(lastMsg.type().name())
                    .content(newContent)
                    .createdAt(LocalDateTime.now())
                    .build();
            messageRepository.save(entity);
        }
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        messageRepository.deleteByMemoryId(memoryId.toString());
        redisService.remove(getCacheKey(memoryId));
    }
    
    private String getCacheKey(Object memoryId) {
        return "yusi:langchain:" + memoryId.toString();
    }
    
    private String extractText(ChatMessage message) {
        if (message instanceof UserMessage) {
            return ((UserMessage) message).singleText();
        } else if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        } else if (message instanceof SystemMessage) {
            return ((SystemMessage) message).text();
        } else if (message instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) message).text();
        }
        return "";
    }
    
    private ChatMessage toChatMessage(ChatMemoryMessage entity) {
        String role = entity.getRole();
        String text = entity.getContent();
        
        switch (role) {
            case "USER": return UserMessage.from(text);
            case "AI": return AiMessage.from(text);
            case "SYSTEM": return SystemMessage.from(text);
            case "TOOL_EXECUTION_RESULT": return ToolExecutionResultMessage.from(null, text); 
            default: return UserMessage.from(text);
        }
    }
}
