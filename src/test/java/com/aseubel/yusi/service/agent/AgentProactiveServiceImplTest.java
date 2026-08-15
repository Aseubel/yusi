package com.aseubel.yusi.service.agent;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.pojo.constant.AgentPersonaStyle;
import com.aseubel.yusi.pojo.constant.ProactiveFrequency;
import com.aseubel.yusi.pojo.constant.TaskExecutionKeys;
import com.aseubel.yusi.pojo.constant.TaskExecutionStatus;
import com.aseubel.yusi.pojo.constant.TaskExecutionType;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.TaskExecution;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.UserNotificationRepository;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.ai.runtime.AgentRunTraceService;
import com.aseubel.yusi.service.agent.impl.AgentProactiveServiceImpl;
import com.aseubel.yusi.service.notification.NotificationService;
import com.aseubel.yusi.service.task.TaskExecutionCommand;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.service.user.UserService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentProactiveServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private AgentPersonaConfigRepository personaConfigRepository;

    @Mock
    private MidTermMemoryRepository midTermMemoryRepository;

    @Mock
    private UserNotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PromptManager promptManager;

    @Mock
    private ChatModel chatModel;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private AgentRunTraceService agentRunTraceService;

    @Mock
    private AgentRunTraceService.RunScope runScope;

    @InjectMocks
    private AgentProactiveServiceImpl service;

    @Test
    void modelFailureFallsBackToTemplateAndCompletesTheGreetingRun() {
        User user = User.builder().userId("user-1").userName("小予").build();
        AgentPersonaConfig config = eligibleConfig();
        TaskExecution execution = TaskExecution.builder()
                .taskId("task-greeting-1")
                .runId("greeting-run-1")
                .status(TaskExecutionStatus.PENDING)
                .build();
        stubEligibleUser(user, config);
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class))).thenReturn(execution);
        when(taskExecutionService.claim(anyString(), anyString(), any())).thenReturn(Optional.of(execution));
        when(agentRunTraceService.open("user-1", "greeting-run-1", "proactive_greeting"))
                .thenReturn(runScope);
        when(midTermMemoryRepository.findValidByUserId(
                eq("user-1"), any(LocalDateTime.class), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of());
        when(promptManager.getSnapshot(PromptKey.AGENT_PROACTIVE_GREETING))
                .thenReturn(new PromptSnapshot("agent-proactive-greeting", "v1", "zh-CN", "问候 {{userName}}"));
        when(chatModel.chat(any(UserMessage.class))).thenThrow(new IllegalStateException("model unavailable"));

        service.scanAndGreet();

        ArgumentCaptor<TaskExecutionCommand> commandCaptor =
                ArgumentCaptor.forClass(TaskExecutionCommand.class);
        verify(taskExecutionService).createOrGet(commandCaptor.capture());
        TaskExecutionCommand command = commandCaptor.getValue();
        assertEquals(TaskExecutionType.PROACTIVE_GREETING, command.getTaskType());
        assertEquals(TaskExecutionKeys.daily(
                TaskExecutionType.PROACTIVE_GREETING, "user-1", "greeting", LocalDate.now()),
                command.getIdempotencyKey());
        verify(notificationService).createNotification(
                eq("user-1"), eq(UserNotification.NotificationType.AGENT_GREETING),
                anyString(), anyString(), eq(null), eq(null), eq(null));
        verify(taskExecutionService).succeed(eq("task-greeting-1"), eq(null), any());
        verify(runScope).complete();
    }

    @Test
    void completedDailyGreetingTaskDoesNotCallModelOrCreateNotificationAgain() {
        User user = User.builder().userId("user-1").userName("小予").build();
        stubEligibleUser(user, eligibleConfig());
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class)))
                .thenReturn(TaskExecution.builder()
                        .taskId("task-greeting-completed")
                        .runId("greeting-run-completed")
                        .status(TaskExecutionStatus.SUCCEEDED)
                        .build());
        when(taskExecutionService.isTerminal(TaskExecutionStatus.SUCCEEDED)).thenReturn(true);

        service.scanAndGreet();

        verify(chatModel, never()).chat(any(UserMessage.class));
        verify(notificationService, never()).createNotification(
                anyString(), eq(UserNotification.NotificationType.AGENT_GREETING),
                anyString(), anyString(), eq(null), eq(null), eq(null));
        verify(taskExecutionService, never()).claim(anyString(), anyString(), any());
        verify(agentRunTraceService, never()).open(anyString(), anyString(), anyString());
    }

    @Test
    void notificationFailureFailsTheGreetingTaskAndAgentRun() {
        User user = User.builder().userId("user-1").userName("小予").build();
        TaskExecution execution = TaskExecution.builder()
                .taskId("task-greeting-failed")
                .runId("greeting-run-failed")
                .status(TaskExecutionStatus.PENDING)
                .build();
        stubEligibleUser(user, eligibleConfig());
        when(taskExecutionService.createOrGet(any(TaskExecutionCommand.class))).thenReturn(execution);
        when(taskExecutionService.claim(anyString(), anyString(), any())).thenReturn(Optional.of(execution));
        when(agentRunTraceService.open("user-1", "greeting-run-failed", "proactive_greeting"))
                .thenReturn(runScope);
        when(midTermMemoryRepository.findValidByUserId(
                eq("user-1"), any(LocalDateTime.class), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of());
        when(promptManager.getSnapshot(PromptKey.AGENT_PROACTIVE_GREETING))
                .thenReturn(new PromptSnapshot("agent-proactive-greeting", "v1", "zh-CN", "问候 {{userName}}"));
        when(notificationService.createNotification(
                eq("user-1"), eq(UserNotification.NotificationType.AGENT_GREETING),
                anyString(), anyString(), eq(null), eq(null), eq(null)))
                .thenThrow(new IllegalStateException("notification unavailable"));

        service.scanAndGreet();

        verify(taskExecutionService).fail(eq("task-greeting-failed"), any(), eq(null), any());
        verify(runScope).fail(anyString());
        verify(taskExecutionService, never()).succeed(anyString(), any(), any());
        verify(runScope, never()).complete();
    }

    private AgentPersonaConfig eligibleConfig() {
        return AgentPersonaConfig.builder()
                .userId("user-1")
                .personalityStyle(AgentPersonaStyle.GENTLE.code())
                .proactiveFrequency(ProactiveFrequency.LOW.code())
                .build();
    }

    private void stubEligibleUser(User user, AgentPersonaConfig config) {
        when(userService.getMatchEnabledUsers()).thenReturn(List.of(user));
        when(personaConfigRepository.findByUserId("user-1")).thenReturn(Optional.of(config));
        when(notificationRepository
                .findByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtDescIdDesc(
                        eq("user-1"), anyString(), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(midTermMemoryRepository.findAvailableByUserId(
                eq("user-1"), any(LocalDateTime.class), eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(MidTermMemory.builder()
                        .createdAt(LocalDateTime.now().minusDays(4))
                        .build()));
    }
}
