package com.aseubel.yusi.service.agent.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserNotificationRepository;
import com.aseubel.yusi.service.agent.AgentProactiveService;
import com.aseubel.yusi.service.notification.NotificationService;
import com.aseubel.yusi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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

    /** 默认未互动天数阈值 */
    private static final int DEFAULT_INACTIVE_DAYS = 3;
    /** 单次扫描最大处理用户数 */
    private static final int MAX_BATCH_SIZE = 50;

    private final UserService userService;
    private final AgentPersonaConfigRepository personaConfigRepository;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final UserNotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Override
    @Scheduled(cron = "0 0 */1 * * ?") // 每小时检查一次
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
                generateGreetingNotification(user, config);
                processed++;
            }

            if (processed > 0) {
                log.info("主动问候扫描完成，发送了 {} 条问候通知", processed);
            }
        } catch (Exception e) {
            log.error("主动问候扫描异常", e);
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
        return config != null && !"off".equalsIgnoreCase(config.getProactiveFrequency());
    }

    private boolean recentlyGreeted(String userId, AgentPersonaConfig config) {
        int days = "normal".equalsIgnoreCase(config.getProactiveFrequency()) ? 3 : 7;
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<UserNotification> recentGreetings = notificationRepository
                .findByUserIdAndTypeAndCreatedAtAfter(userId, "AGENT_GREETING", since);
        return recentGreetings != null && !recentGreetings.isEmpty();
    }

    private boolean meetsInactiveThreshold(String userId) {
        // TODO: 当前简化实现仅检查中期记忆更新时间，应综合检查最近聊天时间 + 日记更新时间
        // 简化实现：检查最近的中期记忆更新时间
        List<MidTermMemory> recentMemories = midTermMemoryRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 1));
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
        // TODO Phase 3 (F8.3): 改为 LLM 基于 mid-memory 动态生成个性化问候，替代当前模板消息
        String greetingMessage = buildGreetingMessage(user, config);
        notificationService.createNotification(
                user.getUserId(),
                "AGENT_GREETING",
                "小予的问候",
                greetingMessage,
                null,
                null,
                null);
        log.info("已为用户 {} 生成主动问候通知", user.getUserId());
    }

    private String buildGreetingMessage(User user, AgentPersonaConfig config) {
        String userName = StrUtil.blankToDefault(user.getUserName(), "朋友");
        // TODO Phase 3 (F8.3): 替换模板消息为 LLM 基于 mid-memory 动态生成的个性化问候
        return switch (config.getPersonalityStyle()) {
            case "lively" -> "嘿 " + userName + "，好久不见！最近过得怎么样？有空来聊聊吧~";
            case "calm" -> userName + "，有一阵子没见了。任何时候你想说话，我都在。";
            case "rational" -> "最近有些新的想法可能对你有帮助，" + userName + "。有空时我们聊聊。";
            default -> userName + "，最近还好吗？有些话想和你说，不急，等你准备好了。";
        };
    }
}
