package com.aseubel.yusi.service.report;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.entity.SoulReport;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.SoulReportRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.notification.NotificationService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.aseubel.yusi.service.user.UserService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoulReportGeneratorTest {

    @Mock
    private SoulReportRepository reportRepository;

    @Mock
    private ChatModel chatModel;

    @Mock
    private PromptManager promptManager;

    @Mock
    private UserService userService;

    @Mock
    private UserPersonaService userPersonaService;

    @Mock
    private MidTermMemoryRepository midTermMemoryRepository;

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private ChatMemoryMessageRepository chatMemoryMessageRepository;

    @Mock
    private AgentPersonaConfigRepository personaConfigRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private AgentRunTraceService agentRunTraceService;

    @Mock
    private AgentRunTraceService.RunScope runScope;

    @InjectMocks
    private SoulReportGenerator generator;

    @Test
    void createsAStablePerUserReportTaskAndStoresItsRunId() {
        User user = User.builder().userId("user-1").userName("小予").build();
        LocalDate periodStart = LocalDate.now().minusDays(7);
        TaskExecution execution = TaskExecution.builder()
                .taskId("task-report-1")
                .runId("report-run-1")
                .status(TaskExecutionStatus.PENDING)
                .build();
        SoulReport saved = SoulReport.builder().build();

        when(userService.getMatchEnabledUsers()).thenReturn(List.of(user));
        when(personaConfigRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(AgentPersonaConfig.builder()
                        .userId("user-1")
                        .weeklyReportEnabled(true)
                        .build()));
        when(reportRepository.existsByUserIdAndReportTypeAndPeriodStart(
                eq("user-1"), anyString(), eq(periodStart))).thenReturn(false);
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class))).thenReturn(execution);
        when(taskExecutionService.claim(anyString(), anyString(), any())).thenReturn(Optional.of(execution));
        when(agentRunTraceService.open("user-1", "report-run-1", "weekly_report"))
                .thenReturn(runScope);
        when(diaryRepository.countByUserIdAndDateRange(eq("user-1"), any(), any())).thenReturn(1L);
        when(promptManager.getSnapshot(PromptKey.SOUL_WEEKLY_REPORT))
                .thenReturn(new PromptSnapshot("soul-weekly-report", "v1", "zh-CN", "# 本周回顾\n{{context}}"));
        when(chatModel.chat(any(UserMessage.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("# 本周回顾\n状态不错")).build());
        when(reportRepository.save(any(SoulReport.class))).thenReturn(saved);

        generator.generateWeeklyReports();

        ArgumentCaptor<TaskExecutionCommand> commandCaptor =
                ArgumentCaptor.forClass(TaskExecutionCommand.class);
        verify(taskExecutionService).createOrGet(commandCaptor.capture());
        TaskExecutionCommand command = commandCaptor.getValue();
        assertEquals(TaskExecutionType.WEEKLY_REPORT, command.getTaskType());
        assertEquals(TaskExecutionKeys.daily(
                TaskExecutionType.WEEKLY_REPORT, "user-1", "weekly", periodStart),
                command.getIdempotencyKey());
        assertTrue(command.getRunId() != null && !command.getRunId().isBlank());

        ArgumentCaptor<SoulReport> reportCaptor = ArgumentCaptor.forClass(SoulReport.class);
        verify(reportRepository, org.mockito.Mockito.atLeastOnce()).save(reportCaptor.capture());
        SoulReport report = reportCaptor.getAllValues().get(0);
        assertEquals("report-run-1", report.getGenerationRunId());
        assertEquals("task-report-1", report.getTaskExecutionId());
        verify(agentRunTraceService).open("user-1", "report-run-1", "weekly_report");
        verify(runScope).complete();
    }
}
