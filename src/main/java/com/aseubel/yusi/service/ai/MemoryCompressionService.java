package com.aseubel.yusi.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.config.MemoryConfigProperties;
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

    private final MemoryConfigProperties memoryConfigProperties;

    private final PromptManager promptManager;

    /**
     * 检查并执行中期记忆总结（基于时间窗口和消息数量）
     * 当用户最后一次对话后超过配置的时间间隔，且未总结的消息达到上下文窗口大小时触发
     *
     * @param memoryId 用户 ID
     */
    @Transactional
    public void checkAndSummarizeMidTermMemory(String memoryId) {
        log.debug("Checking mid-term memory summary for user: {}", memoryId);

        // 获取未总结的消息数量
        long unsummarizedCount = messageRepository.countUnsummarizedMessages(memoryId);
        int contextWindowSize = memoryConfigProperties.getContextWindowSize();

        if (unsummarizedCount < contextWindowSize) {
            log.debug("User {} has {} unsummarized messages, less than context window size {}",
                    memoryId, unsummarizedCount, contextWindowSize);
            return;
        }

        // 获取最后一条消息的时间
        LocalDateTime lastMessageTime = messageRepository.findLastMessageTime(memoryId);
        if (lastMessageTime == null) {
            log.debug("No messages found for user: {}", memoryId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long minutesSinceLastMessage = java.time.Duration.between(lastMessageTime, now).toMinutes();
        long summaryIntervalMinutes = memoryConfigProperties.getMidTermSummaryInterval() / 1000 / 60;

        // 检查是否超过总结时间间隔
        if (minutesSinceLastMessage < summaryIntervalMinutes) {
            log.debug("Last message was {} minutes ago, less than summary interval {} minutes",
                    minutesSinceLastMessage, summaryIntervalMinutes);
            return;
        }

        log.info("Triggering mid-term memory summary for user: {}. Unsummarized: {}, Minutes since last message: {}",
                memoryId, unsummarizedCount, minutesSinceLastMessage);

        // 获取所有未总结的消息（从上次总结开始）
        List<ChatMemoryMessage> unsummarizedMessages = messageRepository
                .findByMemoryIdAndIsSummarizedOrderByCreatedAtAsc(memoryId, false,
                        PageRequest.of(0, contextWindowSize));

        if (CollUtil.isEmpty(unsummarizedMessages)) {
            log.info("No unsummarized messages found for user: {}", memoryId);
            return;
        }

        // 按时间正序排列拼装对话
        String conversationHistory = unsummarizedMessages.stream()
                .filter(m -> !"SYSTEM".equals(m.getRole()))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String promptTemplate = promptManager.getPrompt(PromptKey.MEMORY_EXTRACT);
        String prompt = promptTemplate + "\n\n对话记录:\n" + conversationHistory;

        try {
            String summaryText = chatModel.chat(dev.langchain4j.data.message.UserMessage.from(prompt)).aiMessage()
                    .text();

            if (summaryText == null || summaryText.trim().isEmpty() || summaryText.contains("无关键信息")) {
                log.info("No significant memory extracted for user: {}", memoryId);
                // 即使没有提取到重要信息，也标记为已总结
                markMessagesAsSummarized(unsummarizedMessages);
                return;
            }

            // 保存到 MySQL
            MidTermMemory activeMemory = MidTermMemory.builder()
                    .userId(memoryId)
                    .summary(summaryText.trim())
                    .importance(1.0) // 初始重要性为 1.0
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            activeMemory = midTermMemoryRepository.save(activeMemory);
            log.info("Saved compressed memory to MySQL for user: {}. ID: {}", memoryId, activeMemory.getId());

            // 存储到 Milvus 向量库
            Metadata metadata = new Metadata();
            metadata.put("userId", memoryId);
            metadata.put("memoryId", String.valueOf(activeMemory.getId()));
            metadata.put("createdAt", DateUtil.formatDate(DateUtil.date()));

            TextSegment segment = TextSegment.from(summaryText.trim(), metadata);
            midTermMemoryStore.add(embeddingModel.embed(segment).content(), segment);

            log.info("Saved compressed memory to Milvus for user: {}", memoryId);

            // 标记消息为已总结
            markMessagesAsSummarized(unsummarizedMessages);

        } catch (Exception e) {
            log.error("Failed to compress memory for user: {}", memoryId, e);
        }
    }

    /**
     * 标记消息为已总结
     */
    private void markMessagesAsSummarized(List<ChatMemoryMessage> messages) {
        LocalDateTime now = LocalDateTime.now();
        for (ChatMemoryMessage message : messages) {
            message.setIsSummarized(true);
            message.setSummarizedAt(now);
        }
        messageRepository.saveAll(messages);
        log.info("Marked {} messages as summarized", messages.size());
    }
}
