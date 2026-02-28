package com.aseubel.yusi.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCompressionService {

    private final ChatMemoryMessageRepository messageRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;

    @Qualifier("midTermMemoryStore")
    private final MilvusEmbeddingStore midTermMemoryStore;

    @Qualifier("jsonChatModel")
    private final ChatModel chatModel;

    private final EmbeddingModel embeddingModel;

    private static final String COMPRESSION_PROMPT = """
            请你作为一位极其敏锐的观察者，阅读以下用户与 AI 的对话记录。
            你的任务是：提取出这段对话中用户最重要的信息、经历、情绪或观点。

            提取规则：
            1. 请以第三人称（或"用户"）的视角进行客观总结。
            2. 仅保留能够构成长久回忆的**关键事件**，忽略寒暄、无关紧要的闲聊等。
            3. 提取结果必须精简、具体。

            输出格式（只输出总结的结果，不要其他的任何废话）：
            """;

    /**
     * 压缩指定用户的近期对话记录为中期记忆。
     * 可以由定时任务或触发器调用。
     *
     * @param userId             用户ID
     * @param messagesToCompress 要压缩的消息数量（获取最近的 N 条）
     */
    @Transactional
    public void compressRecentMemory(String userId, int messagesToCompress) {
        log.info("Starting memory compression for user: {}", userId);

        List<ChatMemoryMessage> recentMessages = messageRepository.findByMemoryIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, messagesToCompress));

        if (CollUtil.isEmpty(recentMessages) || recentMessages.size() < 5) {
            log.info("Not enough messages to compress for user: {}", userId);
            return;
        }

        // 按时间正序排列拼装对话
        String conversationHistory = recentMessages.stream()
                .sorted((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                .filter(m -> !"SYSTEM".equals(m.getRole()))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String prompt = COMPRESSION_PROMPT + "\n\n对话记录:\n" + conversationHistory;

        try {
            String summaryText = chatModel.chat(dev.langchain4j.data.message.UserMessage.from(prompt)).aiMessage()
                    .text();

            if (summaryText == null || summaryText.trim().isEmpty() || summaryText.contains("无关键信息")) {
                log.info("No significant memory extracted for user: {}", userId);
                return;
            }

            // 保存到 MySQL
            MidTermMemory activeMemory = MidTermMemory.builder()
                    .userId(userId)
                    .summary(summaryText.trim())
                    .importance(1.0) // 初始重要性为 1.0
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            activeMemory = midTermMemoryRepository.save(activeMemory);
            log.info("Saved compressed memory to MySQL for user: {}. ID: {}", userId, activeMemory.getId());

            // 存储到 Milvus 向量库
            Metadata metadata = new Metadata();
            metadata.put("userId", userId);
            metadata.put("memoryId", String.valueOf(activeMemory.getId()));
            metadata.put("createdAt", DateUtil.formatDate(DateUtil.date()));

            TextSegment segment = TextSegment.from(summaryText.trim(), metadata);
            midTermMemoryStore.add(embeddingModel.embed(segment).content(), segment);

            log.info("Saved compressed memory to Milvus for user: {}", userId);

            // 可以选择在这里清理已经被压缩的聊天记录，但考虑到短期记忆的上下文窗口，此步骤根据业务需求而定。
            // 目前保留在 chat_memory_message 中，交由 LangChain 的 MessageWindowChatMemory 管理。

        } catch (Exception e) {
            log.error("Failed to compress memory for user: {}", userId, e);
        }
    }
}
