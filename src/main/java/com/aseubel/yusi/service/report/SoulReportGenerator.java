package com.aseubel.yusi.service.report;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.*;
import com.aseubel.yusi.repository.*;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.aseubel.yusi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 灵魂周报生成器（F8.3 周期性回顾）。
 * 每周日晚上为开启了周报的用户生成灵魂周报。
 *
 * @author Aseubel
 * @date 2026/06/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulReportGenerator {

    private final SoulReportRepository reportRepository;
    private final SoulReportAssistant reportAssistant;
    private final UserService userService;
    private final UserPersonaService userPersonaService;
    private final MidTermMemoryRepository midTermMemoryRepository;
    private final DiaryRepository diaryRepository;
    private final ChatMemoryMessageRepository chatMemoryMessageRepository;
    private final AgentPersonaConfigRepository personaConfigRepository;
    private final UserNotificationRepository notificationRepository;

    /** 单次扫描最大处理用户数 */
    private static final int MAX_BATCH_SIZE = 50;

    /**
     * 每周日晚上 22:00 执行周报生成。
     */
    @Scheduled(cron = "0 0 22 ? * SUN", zone = "Asia/Shanghai")
    public void generateWeeklyReports() {
        log.info("开始生成灵魂周报...");
        try {
            LocalDate periodStart = LocalDate.now().minusDays(7);
            LocalDate periodEnd = LocalDate.now();
            List<User> matchEnabledUsers = userService.getMatchEnabledUsers();
            int processed = 0;

            for (User user : matchEnabledUsers) {
                if (processed >= MAX_BATCH_SIZE) {
                    break;
                }
                String userId = user.getUserId();
                if (!isWeeklyReportEnabled(userId)) {
                    continue;
                }
                if (reportRepository.existsByUserIdAndReportTypeAndPeriodStart(
                        userId, "WEEKLY", periodStart)) {
                    continue;
                }

                try {
                    SoulReport report = generateReport(user, periodStart, periodEnd);
                    reportRepository.save(report);
                    notifyUser(userId, report);
                    processed++;
                } catch (Exception e) {
                    log.warn("为用户 {} 生成周报失败", userId, e);
                }
            }

            log.info("灵魂周报生成完成，共生成 {} 份", processed);
        } catch (Exception e) {
            log.error("灵魂周报批量生成异常", e);
        }
    }

    private SoulReport generateReport(User user, LocalDate periodStart, LocalDate periodEnd) {
        String userId = user.getUserId();
        String context = buildReportContext(userId, periodStart, periodEnd);
        String markdown = reportAssistant.generateWeeklyReport(context);

        // 提取第一行作为标题
        String title = extractTitle(markdown);

        return SoulReport.builder()
                .userId(userId)
                .reportType("WEEKLY")
                .title(title)
                .content(markdown)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .notified(false)
                .build();
    }

    private String buildReportContext(String userId, LocalDate periodStart, LocalDate periodEnd) {
        StringBuilder ctx = new StringBuilder();

        // 1. 用户基本信息
        User user = userService.getUserByUserId(userId);
        String userName = user != null ? StrUtil.blankToDefault(user.getUserName(), "用户") : "用户";
        ctx.append("用户昵称：").append(userName).append("\n");

        // 2. 中期记忆摘要（近期状态）
        List<MidTermMemory> memories = midTermMemoryRepository
                .findValidByUserId(userId, LocalDateTime.now(), PageRequest.of(0, 5));
        if (!memories.isEmpty()) {
            ctx.append("\n近期状态洞察：\n");
            for (MidTermMemory m : memories) {
                ctx.append("- ").append(m.getSummary()).append("\n");
            }
        }

        // 3. 用户画像
        UserPersona persona = userPersonaService.getUserPersona(userId);
        if (persona != null) {
            ctx.append("\n用户画像：\n");
            if (StrUtil.isNotBlank(persona.getInterests())) {
                ctx.append("- 兴趣：").append(persona.getInterests()).append("\n");
            }
            if (StrUtil.isNotBlank(persona.getTone())) {
                ctx.append("- 偏好语气：").append(persona.getTone()).append("\n");
            }
        }

        // 4. 本周日记统计
        LocalDateTime start = periodStart.atStartOfDay();
        LocalDateTime end = periodEnd.plusDays(1).atStartOfDay();
        long diaryCount = diaryRepository.countByUserIdAndDateRange(userId, start, end);
        ctx.append("\n本周写日记 ").append(diaryCount).append(" 篇");

        List<Diary> recentDiaries = diaryRepository.findTop3ByUserIdOrderByCreateTimeDesc(userId);
        if (!recentDiaries.isEmpty()) {
            ctx.append("，最近几篇的情感标签：");
            String emotions = recentDiaries.stream()
                    .map(Diary::getEmotion)
                    .filter(StrUtil::isNotBlank)
                    .limit(3)
                    .collect(Collectors.joining("、"));
            ctx.append(StrUtil.blankToDefault(emotions, "未标注"));
            ctx.append("\n");
        } else {
            ctx.append("\n");
        }

        // 5. 本周对话互动
        long chatCount = chatMemoryMessageRepository.countByMemoryId(userId);
        ctx.append("与我的对话 ").append(chatCount).append(" 条\n");

        return ctx.toString();
    }

    private String extractTitle(String markdown) {
        if (StrUtil.isBlank(markdown)) {
            return "灵魂周报";
        }
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "");
            }
        }
        return "灵魂周报";
    }

    private boolean isWeeklyReportEnabled(String userId) {
        return personaConfigRepository.findByUserId(userId)
                .map(config -> Boolean.TRUE.equals(config.getWeeklyReportEnabled()))
                .orElse(true); // 默认开启
    }

    private void notifyUser(String userId, SoulReport report) {
        try {
            notificationRepository.save(UserNotification.builder()
                    .userId(userId)
                    .type("SOUL_WEEKLY_REPORT")
                    .title("你的灵魂周报已生成")
                    .content("小予为你准备了一份本周的灵魂周报，点击查看 →")
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build());
            report.setNotified(true);
            reportRepository.save(report);
        } catch (Exception e) {
            log.warn("周报通知发送失败: userId={}", userId, e);
        }
    }
}
