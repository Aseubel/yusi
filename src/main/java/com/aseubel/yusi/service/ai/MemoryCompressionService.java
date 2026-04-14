package com.aseubel.yusi.service.ai;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import com.aseubel.yusi.common.event.ChatCognitionIngestEvent;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.event.MessageSavedEvent;
import com.aseubel.yusi.config.MemoryConfigProperties;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.entity.ChatMemoryMessage;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 中期记忆压缩服务
 * <p>
 * 触发策略（双轨）：
 * <ul>
 * <li><b>主触发（事件驱动）</b>：AI 回复落库后发布
 * {@link MessageSavedEvent}，异步监听立即判断是否需要压缩。</li>
 * <li><b>兜底触发（定时任务）</b>：每 30 分钟扫描有未总结消息的用户，补充处理未经事件覆盖的边界情况。</li>
 * </ul>
 * 压缩策略（双阈值）：
 * <ul>
 * <li><b>硬上限</b>：未总结消息数 >= contextWindowSize * 2 时，不等冷却期强制压缩（适用于持续活跃用户）。</li>
 * <li><b>冷却期</b>：未总结消息数 >= contextWindowSize 且距最后消息超过配置间隔时触发（适用于已停止对话用户）。</li>
 * </ul>
 *
 * @author Aseubel
 * @date 2026/03/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCompressionService {

    private final ChatMemoryMessageRepository messageRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;

    private final MilvusClientV2 milvusClientV2;

    @Qualifier("memoryCompressionAssistant")
    private final MemoryCompressionAssistant memoryCompressionAssistant;

    private final EmbeddingModel embeddingModel;
    private final MemoryConfigProperties memoryConfigProperties;
    private final PromptManager promptManager;
    private final AiLockService aiLockService;
    private final SensitiveDataMaskService sensitiveDataMaskService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 通过 ApplicationContext 获取自身 Spring 代理，确保 @Transactional 方法生效（避免
     * self-invocation 绕过代理）
     */
    private final ApplicationContext applicationContext;

    /** Redis key 前缀（与 AI 请求锁 "ai:lock:" 隔离） */
    private static final String COMPRESS_LOCK_PREFIX = "ai:compress:";

    // ─────────────────────────────────────────────────────────────────────────
    // 事件驱动触发入口
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 监听 {@link MessageSavedEvent}，在 AI 回复所在事务提交后异步触发压缩检查
     * 使用 AFTER_COMMIT 确保消费方能读取到已提交的消息数据
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSaved(MessageSavedEvent event) {
        log.debug("Received MessageSavedEvent for memoryId: {}", event.getMemoryId());
        checkAndSummarizeMidTermMemory(event.getMemoryId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 核心检查方法（定时任务与事件监听共用）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 检查并执行中期记忆总结（基于消息量双阈值策略）
     * <p>
     * 触发条件（满足其一即可）：
     * <ol>
     * <li>未总结消息数 >= hardLimitSize（硬上限，立即压缩）</li>
     * <li>未总结消息数 >= contextWindowSize 且距最后消息超过配置的冷却间隔</li>
     * </ol>
     *
     * @param memoryId 用户 ID
     */
    public void checkAndSummarizeMidTermMemory(String memoryId) {
        log.debug("Checking mid-term memory summary for user: {}", memoryId);

        long unsummarizedCount = messageRepository.countUnsummarizedMessages(memoryId);
        int contextWindowSize = memoryConfigProperties.getContextWindowSize();
        int hardLimitSize = memoryConfigProperties.getHardLimitSize();

        boolean trigger = false;

        if (unsummarizedCount >= hardLimitSize) {
            // 硬上限策略：持续活跃用户不等冷却期
            log.info("Hard limit reached for user: {}. Unsummarized: {} >= hardLimit: {}",
                    memoryId, unsummarizedCount, hardLimitSize);
            trigger = true;
        } else if (unsummarizedCount >= contextWindowSize) {
            // 冷却期策略：停止对话后等待配置的时间间隔
            LocalDateTime lastMessageTime = messageRepository.findLastMessageTime(memoryId);
            if (lastMessageTime == null) {
                log.debug("No messages found for user: {}", memoryId);
                return;
            }
            Duration elapsed = Duration.between(lastMessageTime, LocalDateTime.now());
            Duration interval = memoryConfigProperties.getMidTermSummaryIntervalDuration();
            if (elapsed.compareTo(interval) >= 0) {
                log.info("Cooldown reached for user: {}. Elapsed: {} min, Interval: {} min",
                        memoryId, elapsed.toMinutes(), interval.toMinutes());
                trigger = true;
            } else {
                log.debug("Cooldown not reached for user: {}. Elapsed: {} min < Interval: {} min",
                        memoryId, elapsed.toMinutes(), interval.toMinutes());
            }
        } else {
            log.debug("User {} has {} unsummarized messages, below contextWindowSize {}",
                    memoryId, unsummarizedCount, contextWindowSize);
        }

        if (!trigger) {
            return;
        }

        // 分布式锁防并发（key 与 AI 请求锁隔离）
        String lockKey = COMPRESS_LOCK_PREFIX + memoryId;
        if (!aiLockService.tryAcquireLock(lockKey)) {
            log.debug("Compression already running for user: {}, skipping", memoryId);
            return;
        }

        try {
            doSummarize(memoryId, contextWindowSize);
        } finally {
            aiLockService.releaseLock(lockKey);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部实现
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 执行实际的记忆压缩流程
     */
    private void doSummarize(String memoryId, int contextWindowSize) {
        List<ChatMemoryMessage> unsummarizedMessages = messageRepository
                .findByMemoryIdAndIsSummarizedOrderByCreatedAtAsc(memoryId, false,
                        PageRequest.of(0, contextWindowSize));

        if (CollUtil.isEmpty(unsummarizedMessages)) {
            log.info("No unsummarized messages found for user: {}", memoryId);
            return;
        }

        // 获取此用户最近一次的中期记忆摘要
        List<MidTermMemory> previousMemories = midTermMemoryRepository.findByUserIdOrderByCreatedAtDesc(memoryId,
                PageRequest.of(0, 1));
        String previousSummary = "";
        if (CollUtil.isNotEmpty(previousMemories)) {
            previousSummary = previousMemories.get(0).getSummary();
        }

        // 过滤 SYSTEM 消息，拼装对话历史
        String conversationHistory = unsummarizedMessages.stream()
                .filter(m -> !"SYSTEM".equals(m.getRole()))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        String promptTemplate = promptManager.getPrompt(PromptKey.MEMORY_EXTRACT);

        StringBuilder promptBuilder = new StringBuilder(promptTemplate);
        if (previousSummary != null && !previousSummary.isEmpty()) {
            promptBuilder.append("\n\n之前的记忆摘要:\n").append(previousSummary);
        }
        promptBuilder.append("\n\n对话记录:\n").append(conversationHistory);

        String prompt = promptBuilder.toString();

        try {
            // Step 1: LLM 调用（事务外，避免长时间持有 DB 连接）
            ModelRouteContextHolder
                    .set(ModelRouteContext.builder().scene(PromptKey.MEMORY_EXTRACT.getKey()).language("zh").build());

            String summaryText = memoryCompressionAssistant.extractMemory(prompt);

            ModelRouteContextHolder.clear();

            if (summaryText == null || summaryText.trim().isEmpty() || summaryText.contains("无关键信息")) {
                log.info("No significant memory extracted for user: {}", memoryId);
                // 即使无关键信息，也标记为已总结，防止重复处理
                getSelf().markMessagesAsSummarizedTx(unsummarizedMessages);
                return;
            }

            String trimmedSummary = summaryText.trim();

            // Step 2: MySQL 持久化 + 消息标记（同一事务）
            Long savedMemoryId = getSelf().persistMidTermMemoryTx(memoryId, trimmedSummary, unsummarizedMessages);

            // Step 3: 计算 embedding 并存入 Milvus（使用 V2 客户端，原生插入）
            JsonObject metadata = new JsonObject();
            metadata.addProperty("userId", memoryId);
            metadata.addProperty("memoryId", String.valueOf(savedMemoryId));
            metadata.addProperty("createdAt", DateUtil.formatDate(DateUtil.date()));

            JsonObject row = new JsonObject();
            row.addProperty("id", cn.hutool.core.util.IdUtil.fastUUID());
            row.addProperty("text", trimmedSummary);

            JsonArray vectorArray = new JsonArray();
            for (float v : embeddingModel.embed(trimmedSummary).content().vector()) {
                vectorArray.add(v);
            }
            row.add("vector", vectorArray);
            row.add("metadata", metadata);

            InsertReq insertReq = InsertReq.builder()
                    .collectionName("yusi_mid_term_memory")
                    .data(java.util.Collections.singletonList(row))
                    .build();
            milvusClientV2.insert(insertReq);

            log.info("Compressed memory saved to Milvus for user: {}", memoryId);

        } catch (Exception e) {
            ModelRouteContextHolder.clear();
            log.error("Failed to compress memory for user: {}", memoryId, e);
        }
    }

    /**
     * 将 MidTermMemory 保存到 MySQL 并标记消息为已总结（同一事务）
     *
     * @return 新保存的 MidTermMemory ID（供 Milvus metadata 使用）
     */
    @Transactional
    public Long persistMidTermMemoryTx(String memoryId, String summaryText, List<ChatMemoryMessage> messages) {
        MidTermMemory activeMemory = MidTermMemory.builder()
                .userId(memoryId)
                .summary(summaryText)
                .importance(1.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        activeMemory = midTermMemoryRepository.save(activeMemory);
        log.info("Saved compressed memory to MySQL for user: {}. ID: {}", memoryId, activeMemory.getId());

        markMessagesAsSummarized(messages);
        publishChatCognitionEvent(memoryId, activeMemory);
        return activeMemory.getId();
    }

    /**
     * 仅标记消息为已总结（无有效摘要时使用）
     */
    @Transactional
    public void markMessagesAsSummarizedTx(List<ChatMemoryMessage> messages) {
        markMessagesAsSummarized(messages);
    }

    /**
     * 私有标记方法，仅在已有事务上下文中调用（由 @Transactional 代理方法委托）
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

    /**
     * 获取自身的 Spring AOP 代理实例，确保 {@code @Transactional} 方法的事务拦截生效
     */
    private MemoryCompressionService getSelf() {
        return applicationContext.getBean(MemoryCompressionService.class);
    }

    private void publishChatCognitionEvent(String userId, MidTermMemory memory) {
        if (memory == null || userId == null) {
            return;
        }
        MaskResult maskResult = sensitiveDataMaskService.mask(memory.getSummary());
        String maskedText = maskResult != null ? maskResult.getMaskedText() : memory.getSummary();
        if (maskedText == null || maskedText.isBlank()) {
            return;
        }
        eventPublisher.publishEvent(new ChatCognitionIngestEvent(this, CognitionIngestCommand.builder()
                .userId(userId)
                .sourceType("CHAT_SUMMARY")
                .sourceId(String.valueOf(memory.getId()))
                .maskedText(maskedText)
                .timestamp(memory.getUpdatedAt())
                .confidenceHint(memory.getImportance())
                .build()));
    }
}
