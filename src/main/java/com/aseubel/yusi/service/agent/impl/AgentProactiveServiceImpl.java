package com.aseubel.yusi.service.agent.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.constant.AgentPersonaStyle;
import com.aseubel.yusi.pojo.constant.ProactiveFrequency;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionSourceType;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.constant.TaskFailureCategory;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserNotificationRepository;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteContextHolder;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.agent.AgentProactiveService;
import com.aseubel.yusi.service.notification.NotificationService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.service.user.UserService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 主动问候服务实现。
 * 每小时扫描一次，为符合条件的用户生成主动关怀通知。
 *
 * @author Aseubel
 * @date 2026/06/02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProactiveServiceImpl implements AgentProactiveService {

    private static final String WORKER_ID = "proactive-greeting";
    private static final String RUN_SCENE = "proactive_greeting";
    private static final String GREETING_SOURCE_ID = "greeting";

    /** 默认未互动天数阈值 */
    private static final int DEFAULT_INACTIVE_DAYS = 3;
    /** 单次扫描最大处理用户数 */
    private static final int MAX_BATCH_SIZE = 50;

    private final UserService userService;
    private final AgentPersonaConfigRepository personaConfigRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final UserNotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final PromptManager promptManager;
    private final ChatModel chatModel;
    private final TaskExecutionService taskExecutionService;
    private final AgentRunTraceService agentRunTraceService;

    @Override
    // Called by the centralized scheduler and can also be triggered by an application workflow.
    public void scanAndGreet() {
        log.debug("开始扫描主动问候候选人...");
        try {
            List<User> matchEnabledUsers = userService.getMatchEnabledUsers();
            int processed = 0;

            for (User user : matchEnabledUsers) {
                if (processed >= MAX_BATCH_SIZE) {
                    break;
                }

                AgentPersonaConfig config = getOrCreateConfig(user.getUserId());
                if (!shouldConsiderGreeting(config)) {
                    continue;
                }
                if (recentlyGreeted(user.getUserId(), config)) {
                    continue;
                }
                if (!meetsInactiveThreshold(user.getUserId())) {
                    continue;
                }
                // 不在静默时段
                if (isInQuietHours(config)) {
                    continue;
                }

                // 条件满足，生成主动问候通知
                if (processGreeting(user, config)) {
                    processed++;
                }
            }

            if (processed > 0) {
                log.info("主动问候扫描完成，发送了 {} 条问候通知", processed);
            }
        } catch (Exception e) {
            log.error("主动问候扫描异常", e);
        }
    }

    private boolean processGreeting(User user, AgentPersonaConfig config) {
        TaskExecution execution = null;
        AgentRunTraceService.RunScope scope = null;
        try {
            LocalDate bucket = LocalDate.now();
            String requestedRunId = IdUtil.fastSimpleUUID();
            execution = taskExecutionService.createOrGet(TaskExecutionCommand.builder()
                    .taskType(TaskExecutionType.PROACTIVE_GREETING)
                    .ownerUserId(user.getUserId())
                    .sourceType(TaskExecutionSourceType.PROACTIVE_GREETING.code())
                    .sourceId(GREETING_SOURCE_ID)
                    .sourceVersion(bucket.toString())
                    .runId(requestedRunId)
                    .idempotencyKey(TaskExecutionKeys.daily(
                            TaskExecutionType.PROACTIVE_GREETING, user.getUserId(),
                            GREETING_SOURCE_ID, bucket))
                    .build());
            if (execution == null || taskExecutionService.isTerminal(execution.getStatus())) {
                return false;
            }
            if (StrUtil.isBlank(execution.getRunId())) {
                execution = taskExecutionService.ensureRunId(execution.getTaskId(), requestedRunId);
            }
            String runId = StrUtil.blankToDefault(
                    execution == null ? null : execution.getRunId(), requestedRunId);
            execution = taskExecutionService.claim(
                    execution.getTaskId(), WORKER_ID, LocalDateTime.now()).orElse(null);
            if (execution == null) {
                return false;
            }
            runId = StrUtil.blankToDefault(execution.getRunId(), runId);
            scope = agentRunTraceService.open(user.getUserId(), runId, RUN_SCENE);
            generateGreetingNotification(user, config);
            taskExecutionService.succeed(execution.getTaskId(), null, LocalDateTime.now());
            scope.complete();
            return true;
        } catch (Exception e) {
            if (execution != null) {
                taskExecutionService.fail(execution.getTaskId(), TaskFailureCategory.DEPENDENCY,
                        null, LocalDateTime.now());
            }
            if (scope != null) {
                scope.fail(TaskFailureCategory.DEPENDENCY.name().toLowerCase());
            }
            log.warn("主动问候工作流失败: userId={}", user.getUserId(), e);
            return false;
        } finally {
            if (scope != null) {
                scope.close();
            }
        }
    }

    private AgentPersonaConfig getOrCreateConfig(String userId) {
        return personaConfigRepository.findByUserId(userId)
                .orElseGet(() -> {
                    try {
                        return personaConfigRepository.save(
                                AgentPersonaConfig.builder().userId(userId).build());
                    } catch (Exception e) {
                        // 并发创建时另一个线程可能已插入，回退查询
                        log.debug("并发创建 AgentPersonaConfig 失败，回退查询: userId={}", userId);
                        return personaConfigRepository.findByUserId(userId)
                                .orElseGet(() -> AgentPersonaConfig.builder().userId(userId).build());
                    }
                });
    }

    private boolean shouldConsiderGreeting(AgentPersonaConfig config) {
        return config != null
                && ProactiveFrequency.fromCode(config.getProactiveFrequency()) != ProactiveFrequency.OFF;
    }

    private boolean recentlyGreeted(String userId, AgentPersonaConfig config) {
        int days = ProactiveFrequency.fromCode(config.getProactiveFrequency()) == ProactiveFrequency.NORMAL ? 3 : 7;
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<UserNotification> recentGreetings = notificationRepository
                .findByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtDescIdDesc(
                        userId, UserNotification.NotificationType.AGENT_GREETING.name(), since);
        return recentGreetings != null && !recentGreetings.isEmpty();
    }

    private boolean meetsInactiveThreshold(String userId) {
        // TODO: 当前简化实现仅检查中期记忆更新时间，应综合检查最近聊天时间 + 日记更新时间
        // 简化实现：检查最近的中期记忆更新时间
        List<MidTermMemory> recentMemories = midTermMemoryRepository.findAvailableByUserId(
                userId, LocalDateTime.now(), PageRequest.of(0, 1));
        if (recentMemories.isEmpty()) {
            return false; // 新用户，不打扰
        }
        LocalDateTime lastActivity = recentMemories.get(0).getCreatedAt();
        return lastActivity != null
                && lastActivity.isBefore(LocalDateTime.now().minusDays(DEFAULT_INACTIVE_DAYS));
    }

    private boolean isInQuietHours(AgentPersonaConfig config) {
        if (StrUtil.isBlank(config.getQuietHoursStart()) || StrUtil.isBlank(config.getQuietHoursEnd())) {
            return false;
        }
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(config.getQuietHoursStart(), fmt);
            LocalTime end = LocalTime.parse(config.getQuietHoursEnd(), fmt);
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            } else {
                // 跨午夜情况，如 22:00-08:00
                return !now.isBefore(start) || now.isBefore(end);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void generateGreetingNotification(User user, AgentPersonaConfig config) {
        String userName = StrUtil.blankToDefault(user.getUserName(), "朋友");
        List<MidTermMemory> memories = midTermMemoryRepository.findValidByUserId(
                user.getUserId(), LocalDateTime.now(), PageRequest.of(0, 3));
        String midTermMemories = memories.stream()
                .map(m -> "- " + m.getSummary())
                .collect(Collectors.joining("\n"));

        String greetingMessage;
        try {
            PromptSnapshot snapshot = promptManager.getSnapshot(PromptKey.AGENT_PROACTIVE_GREETING);
            String template = snapshot == null ? "" : snapshot.template();
            String prompt = template
                    .replace("{{userName}}", userName)
                    .replace("{{personalityStyle}}", config.getPersonalityStyle())
                    .replace("{{midTermMemories}}", midTermMemories);

            ModelRouteContextHolder.set(ModelRouteContext.builder()
                    .scene(PromptKey.AGENT_PROACTIVE_GREETING.getKey())
                    .userId(user.getUserId())
                    .prompt(snapshot)
                    .build());
            AiMessage aiMessage;
            try {
                aiMessage = chatModel.chat(UserMessage.from(prompt)).aiMessage();
            } finally {
                ModelRouteContextHolder.clear();
            }
            greetingMessage = aiMessage.text();

            if (StrUtil.isBlank(greetingMessage)) {
                greetingMessage = buildGreetingMessage(user, config);
            }
        } catch (Exception e) {
            log.warn("Failed to generate dynamic greeting using LLM, fallback to template: userId={}", user.getUserId(), e);
            greetingMessage = buildGreetingMessage(user, config);
        }

        notificationService.createNotification(
                user.getUserId(),
                UserNotification.NotificationType.AGENT_GREETING,
                "小予的问候",
                greetingMessage,
                null,
                null,
                null);
        log.info("已为用户 {} 生成主动问候通知", user.getUserId());
    }

    private String buildGreetingMessage(User user, AgentPersonaConfig config) {
        String userName = StrUtil.blankToDefault(user.getUserName(), "朋友");
        return switch (AgentPersonaStyle.fromCode(config.getPersonalityStyle())) {
            case LIVELY -> "嘿 " + userName + "，好久不见！最近过得怎么样？有空来聊聊吧~";
            case CALM -> userName + "，有一阵子没见了。任何时候你想说话，我都在。";
            case RATIONAL -> "最近有些新的想法可能对你有帮助，" + userName + "。有空时我们聊聊。";
            default -> userName + "，最近还好吗？有些话想和你说，不急，等你准备好了。";
        };
    }
}
