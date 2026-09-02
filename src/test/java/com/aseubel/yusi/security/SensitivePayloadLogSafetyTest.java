package com.aseubel.yusi.security;

import com.aseubel.yusi.common.constant.EmotionType;
import com.aseubel.yusi.common.utils.SpelResolverHelper;
import com.aseubel.yusi.config.ai.PersistentChatMemoryStore;
import com.aseubel.yusi.redis.aspect.SpelResolverAspect;
import com.aseubel.yusi.redis.annotation.SpelResolver;
import com.aseubel.yusi.pojo.dto.location.AddLocationRequest;
import com.aseubel.yusi.pojo.entity.CognitiveConflict;
import com.aseubel.yusi.pojo.entity.MidTermMemory;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.pojo.entity.UserLocation;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.pojo.dto.match.MatchRerankResult;
import com.aseubel.yusi.repository.CognitiveConflictRepository;
import com.aseubel.yusi.repository.ChatMemoryMessageRepository;
import com.aseubel.yusi.repository.MidTermMemoryRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.UserLocationRepository;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.ai.chat.ContextBuilderService;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.cognition.CognitiveConflictDetector;
import com.aseubel.yusi.service.cognition.MidMemoryFusionService;
import com.aseubel.yusi.service.event.ProductEventService;
import com.aseubel.yusi.service.match.ConnectionGuideService;
import com.aseubel.yusi.service.match.MatchFeedbackService;
import com.aseubel.yusi.service.match.MatchProfileAssembler;
import com.aseubel.yusi.service.match.SoulConnectionLifecycleService;
import com.aseubel.yusi.service.match.impl.MatchServiceImpl;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import com.aseubel.yusi.service.room.impl.SituationReportService;
import com.aseubel.yusi.service.diary.impl.DiaryServiceImpl;
import com.aseubel.yusi.service.plaza.impl.EmotionAnalyzerImpl;
import com.aseubel.yusi.service.oss.OssService;
import com.aseubel.yusi.service.task.TaskExecutionService;
import com.aseubel.yusi.service.user.UserPersonaService;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.impl.TokenServiceImpl;
import com.aseubel.yusi.service.location.impl.UserLocationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import io.milvus.v2.client.MilvusClientV2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.read.ListAppender;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitivePayloadLogSafetyTest {

    private static final String USER_ID = "fixture-user-payload-safety";
    private static final String PERSONA_SENTINEL = "fixture-persona-payload-7f3c";
    private static final String SITUATION_SENTINEL = "fixture-situation-output-7f3c";
    private static final String CONFLICT_SENTINEL = "fixture-conflict-description-7f3c";
    private static final String FUSION_SENTINEL = "fixture-fusion-reason-7f3c";
    private static final String NAME_SENTINEL = "fixture-display-name-7f3c";
    private static final String LOCATION_SENTINEL = "fixture-location-name-7f3c";
    private static final String DEVICE_SENTINEL = "fixture-device-info-7f3c";
    private static final String IMAGE_SENTINEL = "fixture-image-json-7f3c";
    private static final String SPEL_SENTINEL = "fixture-spel-value-7f3c";
    private static final String EMOTION_SENTINEL = "fixture-raw-emotion-7f3c";

    @Mock
    private UserPersonaService userPersonaService;
    @Mock
    private SituationRoomAgent situationRoomAgent;
    @Mock
    private SituationScenarioRepository scenarioRepository;
    @Mock
    private CognitiveConflictRepository conflictRepository;
    @Mock
    private PromptManager promptManager;
    @Mock
    private ChatModel chatModel;
    @Mock
    private MidTermMemoryRepository midTermMemoryRepository;
    @Mock
    private UserService userService;
    @Mock
    private SoulConnectionLifecycleService connectionLifecycleService;
    @Mock
    private MatchFeedbackService matchFeedbackService;
    @Mock
    private ProductEventService productEventService;
    @Mock
    private MatchProfileAssembler matchProfileAssembler;
    @Mock
    private ConnectionGuideService connectionGuideService;
    @Mock
    private MilvusClientV2 milvusClientV2;
    @Mock
    private dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;
    @Mock
    private ThreadPoolTaskExecutor threadPoolExecutor;
    @Mock
    private TaskExecutionService taskExecutionService;
    @Mock
    private com.aseubel.yusi.repository.SoulMatchRepository soulMatchRepository;
    @Mock
    private com.aseubel.yusi.repository.DiaryRepository diaryRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private UserLocationRepository userLocationRepository;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RScoredSortedSet<String> deviceSet;
    @Mock
    private ChatMemoryMessageRepository messageRepository;
    @Mock
    private IRedisService redisService;
    @Mock
    private ContextBuilderService contextBuilderService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private OssService ossService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;

    private final List<AttachedLogger> attachedLoggers = new ArrayList<>();

    @AfterEach
    void detachLoggers() {
        attachedLoggers.forEach(attached -> {
            attached.logger().detachAppender(attached.appender());
            attached.appender().stop();
            attached.logger().setLevel(attached.originalLevel());
        });
        attachedLoggers.clear();
    }

    @Test
    void personaUpdateDoesNotLogPersonaFields() {
        com.aseubel.yusi.service.ai.tool.UserPersonaTool tool =
                new com.aseubel.yusi.service.ai.tool.UserPersonaTool(userPersonaService);
        ListAppender<ILoggingEvent> appender = attach(com.aseubel.yusi.service.ai.tool.UserPersonaTool.class);

        String result = tool.updateUserPersona(USER_ID, PERSONA_SENTINEL, PERSONA_SENTINEL,
                PERSONA_SENTINEL, PERSONA_SENTINEL, PERSONA_SENTINEL);

        String rendered = rendered(appender);
        assertTrue(result.contains("用户画像已更新"));
        assertFalse(rendered.contains(PERSONA_SENTINEL));
        assertTrue(rendered.contains("updatedFieldCount=5"));
    }

    @Test
    void situationReportDoesNotLogModelOutputOrInvalidFragments() {
        SituationReportService service = new SituationReportService(situationRoomAgent, scenarioRepository,
                new ObjectMapper());
        SituationScenario scenario = new SituationScenario();
        scenario.setId("fixture-scenario-payload");
        scenario.setTitle("fixture-title");
        scenario.setDescription("fixture-description");
        when(scenarioRepository.findById("fixture-scenario-payload")).thenReturn(Optional.of(scenario));

        TokenStream stream = mock(TokenStream.class, RETURNS_SELF);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept("{\"scenarioId\":\"" + SITUATION_SENTINEL
                    + "\",\"personal\":[],\"pairs\":[]}");
            return stream;
        }).when(stream).onPartialResponse(any(Consumer.class));
        doAnswer(invocation -> {
            Consumer<ChatResponse> consumer = invocation.getArgument(0);
            consumer.accept(ChatResponse.builder().aiMessage(AiMessage.from("ignored")).build());
            return stream;
        }).when(stream).onCompleteResponse(any(Consumer.class));
        when(situationRoomAgent.analyzeReport(anyString(), anyString())).thenReturn(stream);

        SituationRoom room = SituationRoom.builder()
                .scenarioId("fixture-scenario-payload")
                .submissions(Map.of(USER_ID, "fixture-answer"))
                .submissionVisibility(Map.of(USER_ID, false))
                .build();
        ListAppender<ILoggingEvent> appender = attach(SituationReportService.class);

        var report = service.analyze(room);

        String rendered = rendered(appender);
        assertNotNull(report);
        assertFalse(rendered.contains(SITUATION_SENTINEL));
        assertTrue(rendered.contains("outputLengthBucket"));
    }

    @Test
    void invalidSituationOutputDoesNotLogTruncatedModelText() {
        SituationReportService service = new SituationReportService(situationRoomAgent, scenarioRepository,
                new ObjectMapper());
        SituationScenario scenario = new SituationScenario();
        scenario.setId("fixture-scenario-invalid");
        when(scenarioRepository.findById("fixture-scenario-invalid")).thenReturn(Optional.of(scenario));

        TokenStream stream = mock(TokenStream.class, RETURNS_SELF);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept("{\"value\":\"" + SITUATION_SENTINEL + "\",}");
            return stream;
        }).when(stream).onPartialResponse(any(Consumer.class));
        doAnswer(invocation -> {
            Consumer<ChatResponse> consumer = invocation.getArgument(0);
            consumer.accept(ChatResponse.builder().aiMessage(AiMessage.from("ignored")).build());
            return stream;
        }).when(stream).onCompleteResponse(any(Consumer.class));
        when(situationRoomAgent.analyzeReport(anyString(), anyString())).thenReturn(stream);

        SituationRoom room = SituationRoom.builder().scenarioId("fixture-scenario-invalid").build();
        ListAppender<ILoggingEvent> appender = attach(SituationReportService.class);

        assertThrows(RuntimeException.class, () -> service.analyze(room));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(SITUATION_SENTINEL));
        assertTrue(rendered.contains("exceptionType"));
    }

    @Test
    void cognitiveConflictDoesNotLogGeneratedDescription() {
        when(userPersonaService.getUserPersona(USER_ID)).thenReturn(UserPersona.builder()
                .interests("fixture-interest")
                .build());
        when(conflictRepository.findTop3ByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(promptManager.getSnapshot(any(com.aseubel.yusi.common.constant.PromptKey.class)))
                .thenReturn(new PromptSnapshot("cognitive-conflict", "fixture-v1", "zh-CN", "{{newObservation}}"));
        when(chatModel.chat(any(UserMessage.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"hasConflict\":true,\"description\":\""
                        + CONFLICT_SENTINEL + "\"}"))
                .build());
        CognitiveConflictDetector detector = new CognitiveConflictDetector(chatModel, promptManager,
                conflictRepository, userPersonaService, new ObjectMapper());
        ListAppender<ILoggingEvent> appender = attach(CognitiveConflictDetector.class);

        detector.checkAndRecord(USER_ID, "fixture-new-insight");

        String rendered = rendered(appender);
        assertFalse(rendered.contains(CONFLICT_SENTINEL));
        assertTrue(rendered.contains("conflictDetected=true"));
    }

    @Test
    void memoryFusionDoesNotLogGeneratedReason() {
        MidTermMemory a = memory(1L, "fixture-memory-a");
        MidTermMemory b = memory(2L, "fixture-memory-b");
        MidTermMemory c = memory(3L, "fixture-memory-c");
        when(midTermMemoryRepository.findUnmergedByUserId(eq(USER_ID)))
                .thenReturn(new ArrayList<>(List.of(a, b, c)));
        when(promptManager.getSnapshot(any(com.aseubel.yusi.common.constant.PromptKey.class)))
                .thenReturn(new PromptSnapshot("memory-fusion", "fixture-v1", "zh-CN",
                        "{{insightA}} {{insightB}} {{timeA}} {{timeB}}"));
        when(chatModel.chat(any(UserMessage.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"isConflict\":true,\"conflictAction\":\"OVERWRITE_B\",\"reason\":\""
                        + FUSION_SENTINEL + "\"}"))
                .build());
        MidMemoryFusionService service = new MidMemoryFusionService(chatModel, promptManager,
                midTermMemoryRepository, userService, new ObjectMapper());
        ListAppender<ILoggingEvent> appender = attach(MidMemoryFusionService.class);

        assertEquals(1, service.fuseUserMemories(USER_ID));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(FUSION_SENTINEL));
        assertTrue(rendered.contains("conflictAction=OVERWRITE_B"));
    }

    @Test
    void matchCreationDoesNotLogDisplayNames() {
        when(promptManager.getSnapshot(any(com.aseubel.yusi.common.constant.PromptKey.class)))
                .thenReturn(new PromptSnapshot("soul-match-letter", "fixture-v1", "zh-CN", "letter"));
        when(chatModel.chat(any(UserMessage.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("fixture-letter"))
                .build());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(threadPoolExecutor).execute(any(Runnable.class));
        when(soulMatchRepository.save(any(com.aseubel.yusi.pojo.entity.SoulMatch.class)))
                .thenAnswer(invocation -> {
                    com.aseubel.yusi.pojo.entity.SoulMatch match = invocation.getArgument(0);
                    match.setId(1L);
                    return match;
                });
        when(productEventService.record(any())).thenReturn(ProductEvent.builder().eventId("fixture-event").build());

        MatchServiceImpl service = new MatchServiceImpl(userService, soulMatchRepository, diaryRepository,
                matchProfileAssembler, connectionGuideService, connectionLifecycleService, matchFeedbackService,
                productEventService, milvusClientV2, embeddingModel, chatModel, promptManager, new ObjectMapper(),
                threadPoolExecutor, taskExecutionService,
                new com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties());
        User userA = User.builder().userId("fixture-user-a").userName(NAME_SENTINEL).build();
        User userB = User.builder().userId("fixture-user-b").userName(NAME_SENTINEL).build();
        var profileA = com.aseubel.yusi.pojo.entity.MatchProfile.builder()
                .userId(userA.getUserId()).profileText("fixture-profile-a").build();
        var profileB = com.aseubel.yusi.pojo.entity.MatchProfile.builder()
                .userId(userB.getUserId()).profileText("fixture-profile-b").build();
        MatchRerankResult rerank = MatchRerankResult.builder()
                .resonance(true).score(88).reason("fixture-reason").build();
        ListAppender<ILoggingEvent> appender = attach(MatchServiceImpl.class);

        ReflectionTestUtils.invokeMethod(service, "createMatch", userA, userB, profileA, profileB, rerank,
                "fixture-generation-run");

        String rendered = rendered(appender);
        assertFalse(rendered.contains(NAME_SENTINEL));
        assertTrue(rendered.contains("fixture-user-a") && rendered.contains("fixture-user-b"));
    }

    @Test
    void locationCreationDoesNotLogLocationName() {
        when(userLocationRepository.save(any(UserLocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UserLocationServiceImpl service = new UserLocationServiceImpl(userLocationRepository);
        AddLocationRequest request = new AddLocationRequest();
        request.setUserId(USER_ID);
        request.setName(LOCATION_SENTINEL);
        request.setLocationType("FREQUENT");
        ListAppender<ILoggingEvent> appender = attach(UserLocationServiceImpl.class);

        assertNotNull(service.addLocation(request));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(LOCATION_SENTINEL));
        assertTrue(rendered.contains("fieldCount"));
    }

    @Test
    void deviceTokenDoesNotLogDeviceInfo() {
        org.mockito.Mockito.doReturn(deviceSet).when(redissonClient).getScoredSortedSet(anyString());
        TokenServiceImpl service = new TokenServiceImpl();
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(service, "jwtProperties", new com.aseubel.yusi.config.JwtProperties());
        ListAppender<ILoggingEvent> appender = attach(TokenServiceImpl.class);

        service.addDeviceToken(USER_ID, "fixture-access-token", DEVICE_SENTINEL);

        String rendered = rendered(appender);
        assertFalse(rendered.contains(DEVICE_SENTINEL));
        assertTrue(rendered.contains("deviceInfoPresent=true"));
    }

    @Test
    void diaryImagePayloadDoesNotAppearInLogs() {
        DiaryServiceImpl service = new DiaryServiceImpl();
        ListAppender<ILoggingEvent> appender = attach(DiaryServiceImpl.class);

        assertThrows(RuntimeException.class, () -> service.convertImagesToUrls(IMAGE_SENTINEL, USER_ID));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(IMAGE_SENTINEL));
        assertTrue(rendered.contains("operation=convert_images"));
    }

    @Test
    void chatImagePayloadDoesNotAppearInLogs() {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(messageRepository, redisService,
                contextBuilderService, eventPublisher, ossService);
        var entity = com.aseubel.yusi.pojo.entity.ChatMemoryMessage.builder()
                .role("USER")
                .content(messagesToJson(List.<ChatMessage>of(UserMessage.from("fixture-chat-text"))))
                .images(IMAGE_SENTINEL)
                .build();
        ListAppender<ILoggingEvent> appender = attach(PersistentChatMemoryStore.class);

        assertNotNull(store.toChatMessage(entity));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(IMAGE_SENTINEL));
        assertTrue(rendered.contains("operation=parse_images"));
    }

    @Test
    void spelExpressionAndResolvedValueDoNotAppearInLogs() throws Throwable {
        Method method = SensitivePayloadLogSafetyTest.class.getDeclaredMethod("spelFixtureMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        SpelResolverHelper helper = new SpelResolverHelper();
        ListAppender<ILoggingEvent> appender = attach(SpelResolverHelper.class);

        Object result = helper.resolveSpel(joinPoint, "'" + SPEL_SENTINEL + "'");

        String rendered = rendered(appender);
        assertEquals(SPEL_SENTINEL, result);
        assertFalse(rendered.contains(SPEL_SENTINEL));
        assertTrue(rendered.contains("operation=spel_resolve"));
    }

    @Test
    void spelAspectDoesNotLogExpressionOrResolvedValue() throws Throwable {
        Method method = SensitivePayloadLogSafetyTest.class.getDeclaredMethod("spelFixtureMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("fixture-proceed-result");
        SpelResolver spelResolver = mock(SpelResolver.class);
        when(spelResolver.expression()).thenReturn("'" + SPEL_SENTINEL + "'");
        SpelResolverAspect aspect = new SpelResolverAspect();
        ListAppender<ILoggingEvent> appender = attach(SpelResolverAspect.class);

        assertEquals("fixture-proceed-result", aspect.resolveSpel(joinPoint, spelResolver));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(SPEL_SENTINEL));
        assertTrue(rendered.contains("operation=spel_resolve"));
    }

    @Test
    void rawEmotionModelOutputDoesNotAppearInLogs() {
        when(promptManager.getSnapshot(any(com.aseubel.yusi.common.constant.PromptKey.class)))
                .thenReturn(new PromptSnapshot("emotion-analysis", "fixture-v1", "zh-CN", "{{content}}"));
        when(chatModel.chat(any(UserMessage.class))).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from(EMOTION_SENTINEL))
                .build());
        EmotionAnalyzerImpl analyzer = new EmotionAnalyzerImpl(chatModel, promptManager);
        ListAppender<ILoggingEvent> appender = attach(EmotionAnalyzerImpl.class);

        assertEquals(EmotionType.NEUTRAL.code(), analyzer.analyzeEmotion("fixture-emotion-input"));

        String rendered = rendered(appender);
        assertFalse(rendered.contains(EMOTION_SENTINEL));
        assertTrue(rendered.contains("emotion=Neutral"));
    }

    private static MidTermMemory memory(Long id, String summary) {
        return MidTermMemory.builder()
                .id(id)
                .userId(USER_ID)
                .summary(summary)
                .importance(0.8)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static void spelFixtureMethod() {
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
