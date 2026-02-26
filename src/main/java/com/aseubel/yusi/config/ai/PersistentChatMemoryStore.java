package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.service.ai.ContextBuilderService;
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
 * 序列化策略：
 * - 所有消息类型都使用 JSON 序列化，以保留完整信息
 * - 特别是 ToolExecutionResultMessage 需要保留 id 和 name
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
    private final ContextBuilderService contextBuilderService;

    private static final int MAX_LOAD_MESSAGES = 100;
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
                // 加入 SystemMessage 到开头
                messages.addFirst(contextBuilderService.buildSystemMessage(memoryId));
                return messages;
            } catch (Exception e) {
                log.warn("Failed to parse chat memory from Redis: {}", e.getMessage());
            }
        }
        
        List<ChatMemoryMessage> entities = messageRepository.findByMemoryIdOrderByCreatedAtDesc(
                memId, PageRequest.of(0, MAX_LOAD_MESSAGES));
        
        if (entities.isEmpty()) {
            return new ArrayList<>();
        }
        // 按创建时间排序（让最近的在后面）
        Collections.reverse(entities);
        // 转换为 ChatMessage 列表
        List<ChatMessage> messages = entities.stream()
                .map(this::toChatMessage)
                .collect(Collectors.toList());
        // 放到缓存下次快速加载
        redisService.setValue(cacheKey, messagesToJson(messages), REDIS_TTL_MS);
        // 将 SystemMessage 加入到消息列表开头
        messages.addFirst(contextBuilderService.buildSystemMessage(memoryId));
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
        
        // 序列化消息（使用 JSON 格式保留完整信息）
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
            // 比较序列化后的内容
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
     * 使用 JSON 格式序列化所有消息类型，以保留完整信息：
     * - AiMessage: 可能包含 toolExecutionRequests
     * - ToolExecutionResultMessage: 需要保留 id 和 name
     * - UserMessage/SystemMessage: 保留完整结构
     */
    private String serializeForDb(ChatMessage message) {
        // 统一使用 JSON 序列化，保留消息的完整结构
        return messagesToJson(List.of(message));
    }
    
    /**
     * 从数据库实体反序列化为 ChatMessage
     * 所有消息都使用 JSON 反序列化
     */
    private ChatMessage toChatMessage(ChatMemoryMessage entity) {
        String content = entity.getContent();
        
        if (content == null || content.isEmpty()) {
            log.warn("Empty content for message with role: {}", entity.getRole());
            return UserMessage.from("");
        }
        
        try {
            // 统一使用 JSON 反序列化
            List<ChatMessage> deserialized = messagesFromJson(content);
            if (!deserialized.isEmpty()) {
                return deserialized.get(0);
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize message, falling back to simple text: {}", e.getMessage());
        }
        
        // 降级处理：如果 JSON 反序列化失败，根据 role 类型创建简单消息
        String role = entity.getRole();
        switch (role) {
            case "AI":
                return AiMessage.from(content);
            case "USER":
                return UserMessage.from(content);
            case "SYSTEM":
                return SystemMessage.from(content);
            default:
                return UserMessage.from(content);
        }
    }
}
