package com.aseubel.yusi.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;
import com.aseubel.yusi.common.exception.GlobalExceptionHandler;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.redis.aspect.CacheAspect;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import com.aseubel.yusi.service.ai.model.ModelInstance;
import com.aseubel.yusi.service.ai.model.ModelProxyFactory;
import com.aseubel.yusi.service.ai.model.ModelRouteCandidate;
import com.aseubel.yusi.service.ai.model.ModelRouteContext;
import com.aseubel.yusi.service.ai.model.ModelRouteDecision;
import com.aseubel.yusi.service.ai.model.ModelRouterService;
import com.aseubel.yusi.service.ai.model.ModelStateCenter;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptService;
import com.aseubel.yusi.service.user.AdminService;
import com.aseubel.yusi.service.user.TokenService;
import com.aseubel.yusi.service.user.impl.AdminServiceImpl;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.milvus.v2.client.MilvusClientV2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RScript;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveExceptionLogSafetyTest {

    private static final String EXCEPTION_MESSAGE = "fixture-exception-message-7f3c";
    private static final String MODEL_ERROR = "fixture-model-error-7f3c";
    private static final String PROMPT_ERROR = "fixture-prompt-error-7f3c";
    private static final String ADMIN_ERROR = "fixture-admin-error-7f3c";
    private static final String CACHE_KEY = "fixture-cache-key-7f3c";
    private static final String CACHE_ERROR = "fixture-cache-error-7f3c";

    @Mock
    private ModelRouterService modelRouterService;
    @Mock
    private ModelStateCenter modelStateCenter;
    @Mock
    private SensitiveDataMaskService maskService;
    @Mock
    private ChatModel chatModel;
    @Mock
    private StreamingChatModel streamingChatModel;
    @Mock
    private PromptService promptService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private SituationRoomRepository situationRoomRepository;
    @Mock
    private SituationScenarioRepository situationScenarioRepository;
    @Mock
    private SuggestionRepository suggestionRepository;
    @Mock
    private InterfaceDailyUsageRepository interfaceDailyUsageRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TokenService tokenService;
    @Mock
    private IRedisService redissonService;
    @Mock
    private MilvusClientV2 milvusClientV2;
    @Mock
    private com.aseubel.yusi.service.security.SecurityAuditService securityAuditService;
    @Mock
    private ThreadPoolTaskExecutor threadPoolExecutor;
    @Mock
    private com.aseubel.yusi.common.utils.SpelResolverHelper spelResolverHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private IRedisService cacheRedisService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;
    @Mock
    private QueryCache queryCache;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private final List<AttachedLogger> attachedLoggers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        attachedLoggers.forEach(attached -> {
            attached.logger().detachAppender(attached.appender());
            attached.appender().stop();
            attached.logger().setLevel(attached.originalLevel());
        });
        attachedLoggers.clear();
        RequestContextHolder.resetRequestAttributes();
        UserContext.clear();
    }

    @Test
    void globalHandlerDoesNotLogSseExceptionMessageOrThrowable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        when(response.isCommitted()).thenReturn(true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        ListAppender<ILoggingEvent> appender = attach(GlobalExceptionHandler.class);

        assertNull(handler.handleException(new IllegalStateException(EXCEPTION_MESSAGE)));

        assertNoSensitiveThrowable(appender, EXCEPTION_MESSAGE);
    }

    @Test
    void globalHandlerDoesNotLogUnhandledExceptionMessageOrThrowable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        when(response.isCommitted()).thenReturn(false);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        ListAppender<ILoggingEvent> appender = attach(GlobalExceptionHandler.class);

        var result = handler.handleException(new IllegalStateException(EXCEPTION_MESSAGE));

        assertEquals(500, result.getCode());
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNoSensitiveThrowable(appender, EXCEPTION_MESSAGE);
    }

    @Test
    void modelProxyDoesNotLogProviderErrorMessage() {
        ModelInstance selected = ModelInstance.builder()
                .id("fixture-model-id")
                .modelName("fixture-model-name")
                .provider("fixture-provider")
                .scenes(Set.of())
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
        ModelRouteDecision decision = new ModelRouteDecision(
                "fixture-request-id", "fixture-scene", 1L, "fixture-tier", List.of(),
                List.of(new ModelRouteCandidate("fixture-tier", selected, true, null)),
                "fixture-route-reason");
        when(modelRouterService.plan(any(ModelRouteContext.class))).thenReturn(decision);
        when(modelStateCenter.allowRequest("fixture-model-id")).thenReturn(true);
        when(maskService.mask(anyString())).thenReturn(
                com.aseubel.yusi.service.ai.mask.MaskResult.noMask("fixture-message"));
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException(MODEL_ERROR));
        ListAppender<ILoggingEvent> appender = attach(ModelProxyFactory.class);

        ModelProxyFactory factory = new ModelProxyFactory(modelRouterService, modelStateCenter, maskService);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> factory.createChatProxy("fixture-scene")
                        .chat(ChatRequest.builder().messages(List.of(UserMessage.from("fixture-message"))).build()));

        assertNoSensitiveThrowable(appender, MODEL_ERROR);
    }

    @Test
    void promptManagerDoesNotLogDatabaseOrClasspathExceptionMessage() {
        when(promptService.getPromptTemplate("fixture-prompt", "zh-CN"))
                .thenThrow(new IllegalStateException(PROMPT_ERROR));
        doThrow(new IllegalStateException(PROMPT_ERROR)).when(promptService)
                .savePrompt(any(), anyString());
        PromptManager manager = new PromptManager(promptService);
        ListAppender<ILoggingEvent> appender = attach(PromptManager.class);

        manager.loadPrompt("fixture-prompt");

        assertNoSensitiveThrowable(appender, PROMPT_ERROR);
    }

    @Test
    void adminCleanupDoesNotLogSqlOrExceptionMessage() {
        UserContext.setUserId("fixture-admin-id");
        org.mockito.Mockito.when(milvusClientV2.getLoadState(
                        org.mockito.ArgumentMatchers.any(io.milvus.v2.service.collection.request.GetLoadStateReq.class)))
                .thenReturn(true);
        when(userRepository.findByUserId("fixture-target-id"))
                .thenReturn(com.aseubel.yusi.pojo.entity.User.builder().userId("fixture-target-id")
                        .permissionLevel(1).build());
        when(userRepository.findByUserId("fixture-admin-id"))
                .thenReturn(com.aseubel.yusi.pojo.entity.User.builder().userId("fixture-admin-id")
                        .permissionLevel(5).build());
        doThrow(new IllegalStateException(ADMIN_ERROR)).when(tokenService).deleteRefreshToken("fixture-target-id");
        ListAppender<ILoggingEvent> appender = attach(AdminServiceImpl.class);

        AdminService service = new AdminServiceImpl(userRepository, diaryRepository, situationRoomRepository,
                situationScenarioRepository, suggestionRepository, interfaceDailyUsageRepository, jdbcTemplate,
                tokenService, redissonService, milvusClientV2, securityAuditService);
        org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> service.deregisterUser("fixture-target-id"));

        assertNoSensitiveThrowable(appender, ADMIN_ERROR);
        assertFalse(rendered(appender).contains("DELETE FROM"));
    }

    @Test
    void cacheAspectDoesNotLogKeyOrExceptionMessage() throws Throwable {
        CacheAspect aspect = new CacheAspect(threadPoolExecutor, spelResolverHelper, cacheRedisService, objectMapper);
        ReflectionTestUtils.setField(aspect, "keyPrefix", "fixture-prefix:");
        ReflectionTestUtils.setField(aspect, "ttl", Duration.ofSeconds(1));
        when(spelResolverHelper.resolveSpel(joinPoint, "fixture-expression")).thenReturn(CACHE_KEY);
        when(queryCache.key()).thenReturn("fixture-expression");
        when(queryCache.ttl()).thenReturn(1L);
        when(queryCache.compress()).thenReturn(false);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(
                SensitiveExceptionLogSafetyTest.class
                        .getDeclaredMethod("cacheAspectDoesNotLogKeyOrExceptionMessage"));
        when(cacheRedisService.execute(anyString(), anyString(), any(RScript.ReturnType.class), anyList(),
                any(Object[].class)))
                .thenReturn(java.util.Arrays.asList(null, "NEED_QUERY"));
        when(joinPoint.proceed()).thenThrow(new IllegalStateException(CACHE_ERROR));
        ListAppender<ILoggingEvent> appender = attach(CacheAspect.class);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> aspect.queryCache(joinPoint, queryCache));

        assertNoSensitiveThrowable(appender, CACHE_ERROR);
        assertFalse(rendered(appender).contains(CACHE_KEY));
    }

    private ListAppender<ILoggingEvent> attach(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        attachedLoggers.add(new AttachedLogger(logger, appender, originalLevel));
        return appender;
    }

    private void assertNoSensitiveThrowable(ListAppender<ILoggingEvent> appender, String sentinel) {
        assertFalse(rendered(appender).contains(sentinel));
        assertTrue(appender.list.stream().allMatch(event -> event.getThrowableProxy() == null));
    }

    private String rendered(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage() + " " + throwableText(event.getThrowableProxy()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String throwableText(IThrowableProxy proxy) {
        if (proxy == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(proxy.getClassName());
        text.append(':').append(proxy.getMessage());
        for (StackTraceElementProxy frame : proxy.getStackTraceElementProxyArray()) {
            text.append(' ').append(frame.getStackTraceElement());
        }
        return text.toString();
    }

    private record AttachedLogger(Logger logger, ListAppender<ILoggingEvent> appender, Level originalLevel) {
    }
}
