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
 * 注意：SystemMessage 不存储到数据库，因为 LangChain4j 每次请求都会通过
 * systemMessageProvider 动态生成，存储会导致重复。
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
                List<ChatMessage> messages = messagesFromJson(json);
                // 过滤掉 SystemMessage，因为每次请求都会由 systemMessageProvider 动态生成
                return messages.stream()
                        .filter(msg -> !(msg instanceof SystemMessage))
                        .collect(Collectors.toList());
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
                // 过滤掉 SystemMessage
                .filter(msg -> !(msg instanceof SystemMessage))
                .collect(Collectors.toList());

        redisService.setValue(cacheKey, messagesToJson(messages), REDIS_TTL_MS);
        
        return messages;
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String memId = memoryId.toString();
        String cacheKey = getCacheKey(memId);
        
        // 过滤掉 SystemMessage 后再存储到 Redis
        // SystemMessage 每次请求都会由 systemMessageProvider 动态生成，不应持久化
        List<ChatMessage> messagesWithoutSystem = messages.stream()
                .filter(msg -> !(msg instanceof SystemMessage))
                .collect(Collectors.toList());
        
        // 更新 Redis 缓存（不包含 SystemMessage）
        redisService.setValue(cacheKey, messagesToJson(messagesWithoutSystem), REDIS_TTL_MS);

        if (messagesWithoutSystem.isEmpty()) return;

        ChatMessage lastMsg = messagesWithoutSystem.get(messagesWithoutSystem.size() - 1);
        
        // 检查是否需要插入新消息
        String serializedLastMsg = serializeForDb(lastMsg);
        if (serializedLastMsg == null) {
            log.debug("Skipping message with null content: {}", lastMsg.type());
            return;
        }
        
        List<ChatMemoryMessage> lastDbMsgs = messageRepository.findByMemoryIdOrderByCreatedAtDesc(
                memId, PageRequest.of(0, 1));
        
        boolean shouldInsert = true;
        if (!lastDbMsgs.isEmpty()) {
            ChatMemoryMessage lastDb = lastDbMsgs.get(0);
            // 对于 AI 消息，比较序列化后的内容
            if (lastDb.getContent().equals(serializedLastMsg)) {
                shouldInsert = false;
            }
        }
        
        if (shouldInsert) {
            ChatMemoryMessage entity = ChatMemoryMessage.builder()
                    .memoryId(memId)
                    .role(lastMsg.type().name())
                    .content(serializedLastMsg)
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
    
    /**
     * 序列化消息用于数据库存储
     * - AiMessage 包含 toolExecutionRequests 时，序列化为 JSON
     * - 其他消息类型直接提取文本
     */
    private String serializeForDb(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            // 如果包含工具调用，序列化整个 AiMessage
            if (aiMessage.hasToolExecutionRequests()) {
                return messagesToJson(List.of(aiMessage));
            }
            return aiMessage.text();
        } else if (message instanceof UserMessage) {
            return ((UserMessage) message).singleText();
        } else if (message instanceof ToolExecutionResultMessage) {
            return ((ToolExecutionResultMessage) message).text();
        } else if (message instanceof SystemMessage) {
            return ((SystemMessage) message).text();
        }
        return null;
    }
    
    /**
     * 从数据库实体反序列化为 ChatMessage
     * - AI 消息可能包含工具调用，需要特殊处理
     */
    private ChatMessage toChatMessage(ChatMemoryMessage entity) {
        String role = entity.getRole();
        String content = entity.getContent();
        
        switch (role) {
            case "AI":
                // 检查是否是序列化的 AiMessage（包含工具调用）
                if (content != null && content.startsWith("[{") && content.contains("toolExecutionRequests")) {
                    try {
                        List<ChatMessage> deserialized = messagesFromJson(content);
                        if (!deserialized.isEmpty() && deserialized.get(0) instanceof AiMessage) {
                            return deserialized.get(0);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to deserialize AiMessage with tool calls: {}", e.getMessage());
                    }
                }
                return AiMessage.from(content);
            case "USER":
                return UserMessage.from(content);
            case "SYSTEM":
                return SystemMessage.from(content);
            case "TOOL_EXECUTION_RESULT":
                return ToolExecutionResultMessage.from(null, content);
            default:
                return UserMessage.from(content);
        }
    }
}
