package com.aseubel.yusi.service.cognition;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.service.ai.PromptManager;
import com.aseubel.yusi.service.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 跨源记忆融合服务（F11.4）。
 * 定时扫描用户的 mid-memory 条目，LLM 语义去重，将同一主题的多条洞察合并为一条。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidMemoryFusionService {

    @Qualifier("chatModel")
    private final ChatModel chatModel;
    private final PromptManager promptManager;
    private final MidTermMemoryRepository memoryRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /** 触发融合的最小条目数 */
    private static final int MIN_ENTRIES_TO_FUSE = 3;
    /** 单次扫描最大用户数 */
    private static final int MAX_BATCH_USERS = 30;
    /** 每个用户最多比较的对数 */
    private static final int MAX_PAIRS_PER_USER = 5;

    /**
     * 每天凌晨 3:00 执行跨源融合。
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Asia/Shanghai")
    public void runFusion() {
        log.info("开始跨源记忆融合...");
        try {
            List<User> users = userService.getMatchEnabledUsers();
            int processed = 0;
            int totalMerged = 0;

            for (User user : users) {
                if (processed >= MAX_BATCH_USERS) { break; }
                try {
                    int merged = fuseUserMemories(user.getUserId());
                    if (merged > 0) { processed++; totalMerged += merged; }
                } catch (Exception e) {
                    log.warn("用户 {} 记忆融合失败", user.getUserId(), e);
                }
            }

            log.info("跨源记忆融合完成: 处理{}人, 合并{}对", processed, totalMerged);
        } catch (Exception e) {
            log.error("跨源记忆融合批量异常", e);
        }
    }

    /**
     * 融合指定用户的记忆。返回合并的对数。
     */
    public int fuseUserMemories(String userId) {
        List<MidTermMemory> entries = memoryRepository.findUnmergedByUserId(userId, LocalDateTime.now());
        if (entries.size() < MIN_ENTRIES_TO_FUSE) {
            return 0;
        }

        int merged = 0;
        // 滑动窗口：相邻两条对比（按 createdAt 倒序，最新在前）
        for (int i = 0; i < Math.min(entries.size() - 1, MAX_PAIRS_PER_USER); i++) {
            MidTermMemory a = entries.get(i);
            MidTermMemory b = entries.get(i + 1);
            if (tryMerge(userId, a, b)) { merged++; }
        }

        return merged;
    }

    private boolean tryMerge(String userId, MidTermMemory a, MidTermMemory b) {
        if (a.getId().equals(b.getId())) { return false; }
        if (a.getMergedIntoId() != null || b.getMergedIntoId() != null) { return false; }

        try {
            String template = promptManager.getPrompt(PromptKey.MEMORY_FUSION);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timeA = a.getCreatedAt() != null ? a.getCreatedAt().format(formatter) : "未知";
            String timeB = b.getCreatedAt() != null ? b.getCreatedAt().format(formatter) : "未知";

            String prompt = template
                    .replace("{{insightA}}", a.getSummary())
                    .replace("{{insightB}}", b.getSummary())
                    .replace("{{timeA}}", timeA)
                    .replace("{{timeB}}", timeB);

            AiMessage aiMessage = chatModel.chat(UserMessage.from(prompt)).aiMessage();
            String raw = aiMessage.text();

            JsonNode result = objectMapper.readTree(extractJson(raw));

            // Case 1: Merge
            if (result.has("shouldMerge") && result.get("shouldMerge").asBoolean()) {
                String mergedSummary = result.has("mergedSummary")
                        ? result.get("mergedSummary").asText()
                        : a.getSummary();

                // Keep the one with higher importance, link the other
                if (a.getImportance() >= b.getImportance()) {
                    a.setSummary(mergedSummary);
                    memoryRepository.save(a);
                    b.setMergedIntoId(a.getId());
                    memoryRepository.save(b);
                } else {
                    b.setSummary(mergedSummary);
                    memoryRepository.save(b);
                    a.setMergedIntoId(b.getId());
                    memoryRepository.save(a);
                }
                log.info("融合记忆成功: userId={}, keeper={}, merged={}", userId,
                        a.getImportance() >= b.getImportance() ? a.getId() : b.getId(),
                        a.getImportance() >= b.getImportance() ? b.getId() : a.getId());
                return true;
            }

            // Case 2: Conflict Overwrite
            if (result.has("isConflict") && result.get("isConflict").asBoolean()) {
                String conflictAction = result.has("conflictAction") ? result.get("conflictAction").asText() : "NONE";
                if ("OVERWRITE_B".equalsIgnoreCase(conflictAction)) {
                    // Make the older memory b expire immediately
                    b.setValidUntil(LocalDateTime.now());
                    b.setMergedIntoId(a.getId());
                    memoryRepository.save(b);
                    log.info("记忆冲突覆写完成: userId={}, 新记忆(保留)={}, 旧记忆(失效)={}, 原因={}",
                            userId, a.getId(), b.getId(), result.has("reason") ? result.get("reason").asText() : "");
                    return true;
                }
            }

        } catch (Exception e) {
            log.warn("记忆融合/冲突处理 LLM 调用失败: userId={}", userId, e);
        }
        return false;
    }

    private String extractJson(String raw) {
        if (raw == null) { return "{}"; }
        int s = raw.indexOf('{'), e = raw.lastIndexOf('}');
        return (s >= 0 && e > s) ? raw.substring(s, e + 1) : "{}";
    }
}
