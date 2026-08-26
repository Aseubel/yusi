package com.aseubel.yusi.security;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.pojo.constant.ResonanceType;
import com.aseubel.yusi.pojo.constant.RoomStatus;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.model.ModelManagementService;
import com.aseubel.yusi.service.ai.prompt.PromptService;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.lifegraph.LifeGraphDataService;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.notification.NotificationService;
import com.aseubel.yusi.service.oss.OssService;
import com.aseubel.yusi.service.plaza.ResonanceSignalService;
import com.aseubel.yusi.service.plaza.SoulPlazaService;
import com.aseubel.yusi.service.room.SituationRoomService;
import com.aseubel.yusi.service.user.TokenService;
import com.aseubel.yusi.service.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * application-invariant-only authorization checks using H2 and Mockito
 * boundaries. These tests do not claim real dependency or deployment
 * penetration coverage.
 */
@SpringBootTest(properties = "yusi.rate-limit.hmac-secret=fixture-rate-secret-authz")
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
@AutoConfigureMockMvc
class AuthzBoundaryMockMvcTest {

    private static final String USER = "fixture-user-authz";
    private static final String OTHER_USER = "fixture-user-other-authz";
    private static final String ADMIN = "fixture-user-admin-authz";
    private static final String DIARY = "fixture-diary-authz";
    private static final String ROOM = "fixture-room-authz";
    private static final String OBJECT_KEY = "fixture-object-key-authz";
    private static final String PROMPT = "fixture-prompt-authz";
    private static final String CARD_CONTENT = "fixture-card-content-authz";
    private static final String SIGNAL_MESSAGE = "fixture-message-authz";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SituationRoomRepository situationRoomRepository;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private UserService userService;

    @MockBean
    private PromptService promptService;

    @MockBean
    private ModelManagementService modelManagementService;

    @MockBean
    private DiaryService diaryService;

    @MockBean
    private MatchService matchService;

    @MockBean
    private SituationRoomService situationRoomService;

    @MockBean
    private OssService ossService;

    @MockBean
    private LifeGraphDataService lifeGraphDataService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private SoulPlazaService soulPlazaService;

    @MockBean
    private ResonanceSignalService resonanceSignalService;

    @Autowired
    private RedissonClient redissonClient;

    private RRateLimiter rateLimiter;

    @BeforeEach
    void setUpFixtureBoundaries() {
        reset(tokenService, userService, promptService, modelManagementService, diaryService,
                matchService, situationRoomService, ossService, lifeGraphDataService, notificationService,
                soulPlazaService, resonanceSignalService, redissonClient);

        when(tokenService.isBlacklisted(anyString())).thenReturn(false);
        when(tokenService.isValidDeviceToken(anyString(), anyString())).thenReturn(true);
        when(userService.checkAdmin(anyString())).thenReturn(false);

        rateLimiter = org.mockito.Mockito.mock(RRateLimiter.class);
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.trySetRate(any(RateType.class), anyInt(), anyInt(), any(RateIntervalUnit.class)))
                .thenReturn(true);
        when(rateLimiter.expire(any(Duration.class))).thenReturn(true);
        when(rateLimiter.tryAcquire()).thenReturn(true);

        saveFixtureUser(USER, 0);
        saveFixtureUser(OTHER_USER, 0);
        saveFixtureUser(ADMIN, 99);
        situationRoomRepository.findById(ROOM).ifPresent(situationRoomRepository::delete);
        situationRoomRepository.save(SituationRoom.builder()
                .code(ROOM)
                .status(RoomStatus.IN_PROGRESS)
                .ownerId(OTHER_USER)
                .members(new HashSet<>(Set.of(OTHER_USER)))
                .submissions(new HashMap<>())
                .submissionVisibility(new HashMap<>())
                .cancelVotes(new HashSet<>())
                .createdAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void clearFixtureState() {
        UserContext.clear();
        situationRoomRepository.findById(ROOM).ifPresent(situationRoomRepository::delete);
    }

    @Test
    void authz001_nonAdminPromptReadReturnsFixedForbiddenWithoutServiceCall() throws Exception {
        MvcResult result = mockMvc.perform(authenticated(get("/api/prompt/{name}", PROMPT), USER))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("code").asInt())
                .as("AUTHZ-001 must become an admin-only prompt read")
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
        verify(promptService, never()).getPrompt(PROMPT, "zh-CN");
    }

    @Test
    void authz001_adminPromptReadReturnsPromptAndCallsServiceOnce() throws Exception {
        when(userService.checkAdmin(ADMIN)).thenReturn(true);
        when(promptService.getPrompt(PROMPT, "zh-CN")).thenReturn("fixture-prompt-response-authz");

        mockMvc.perform(authenticated(get("/api/prompt/{name}", PROMPT), ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").value("fixture-prompt-response-authz"));

        verify(promptService, times(1)).getPrompt(PROMPT, "zh-CN");
    }

    @Test
    void rejectsNonAdminAdminModelAndPromptSearchBeforeProtectedServices() throws Exception {
        expectBusinessForbidden(authenticated(get("/api/admin/me"), USER));
        expectBusinessForbidden(authenticated(get("/api/model/states"), USER));
        expectBusinessForbidden(authenticated(get("/api/prompt/search"), USER));

        verifyNoInteractions(modelManagementService, promptService);
    }

    @Test
    void rejectsAnonymousDiaryAiHistoryAndModelStateWithTokenMissing() throws Exception {
        expectTokenMissing(get("/api/diary/{diaryId}", DIARY));
        expectTokenMissing(get("/api/ai/chat/history"));
        expectTokenMissing(get("/api/model/states"));

        verifyNoInteractions(diaryService, modelManagementService);
    }

    @Test
    void h01_rejectsOtherUsersDiaryListBeforeDiaryService() throws Exception {
        expectAuthorizationForbidden(authenticated(get("/api/diary/list")
                .param("userId", OTHER_USER), USER));

        verifyNoInteractions(diaryService);
    }

    @Test
    void h02_requiresMatchServiceToRejectOtherUsersMatchAction() throws Exception {
        when(matchService.handleMatchAction(USER, 2001L, 1))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        expectBusinessForbidden(authenticated(post("/api/match/{matchId}/action", 2001L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":1}"), USER));

        verify(matchService, times(1)).handleMatchAction(USER, 2001L, 1);
    }

    @Test
    void h03_rejectsNonMemberRoomChatWithH2MembershipBoundary() throws Exception {
        expectBusinessForbidden(authenticated(get("/api/room-chat/history")
                .param("roomCode", ROOM), USER));

        assertThat(situationRoomRepository.findById(ROOM).orElseThrow().getMembers())
                .containsExactly(OTHER_USER);
    }

    @Test
    void h04_requiresSituationRoomServiceToRejectOtherUsersRoom() throws Exception {
        when(situationRoomService.getRoomDetailResponse(ROOM, USER))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        expectBusinessForbidden(authenticated(get("/api/room/{code}", ROOM), USER));

        verify(situationRoomService, times(1)).getRoomDetailResponse(ROOM, USER);
    }

    @Test
    void h05_keepsObjectKeyOwnershipAtOssBoundary() throws Exception {
        when(ossService.generateOwnedUrl(OBJECT_KEY, USER))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        expectBusinessForbidden(authenticated(get("/api/image/url")
                .param("objectKey", OBJECT_KEY), USER));

        verify(ossService, times(1)).generateOwnedUrl(OBJECT_KEY, USER);
    }

    @Test
    void h06_requiresLifeGraphServiceToRejectOtherUsersEntityReference() throws Exception {
        when(lifeGraphDataService.getGraphBfs(USER, 3001L, 2, 500))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        expectBusinessForbidden(authenticated(get("/api/lifegraph/graph/bfs")
                .param("centerId", "3001"), USER));

        verify(lifeGraphDataService, times(1)).getGraphBfs(USER, 3001L, 2, 500);
    }

    @Test
    void h06b_mapsRawLifeGraphSecurityExceptionToFixedForbidden() throws Exception {
        doThrow(new SecurityException("fixture-cross-user-relation"))
                .when(lifeGraphDataService).deleteRelation(USER, 3002L);

        mockMvc.perform(authenticated(delete("/api/lifegraph/relations/{id}", 3002L), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.info").value(ErrorCode.FORBIDDEN.getMsg()));

        verify(lifeGraphDataService, times(1)).deleteRelation(USER, 3002L);
    }

    @Test
    void h07_requiresNotificationServiceToRejectOtherUsersNotification() throws Exception {
        when(notificationService.getNotifications(USER, 0, 20, null))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        expectBusinessForbidden(authenticated(get("/api/notifications"), USER));

        verify(notificationService, times(1)).getNotifications(USER, 0, 20, null);
    }

    @Test
    void h08_rejectsOtherUsersPlazaCardUpdateDeleteAndResonance() throws Exception {
        when(soulPlazaService.updateCard(USER, 4001L, CARD_CONTENT))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(soulPlazaService).deleteCard(USER, 4001L);
        when(soulPlazaService.resonate(USER, 4001L, ResonanceType.EMPATHY))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        expectBusinessForbidden(authenticated(put("/api/plaza/{cardId}", 4001L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + CARD_CONTENT + "\"}"), USER));
        expectBusinessForbidden(authenticated(delete("/api/plaza/{cardId}", 4001L), USER));
        expectBusinessForbidden(authenticated(post("/api/plaza/{cardId}/resonate", 4001L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"EMPATHY\"}"), USER));

        verify(soulPlazaService, times(1)).updateCard(USER, 4001L, CARD_CONTENT);
        verify(soulPlazaService, times(1)).deleteCard(USER, 4001L);
        verify(soulPlazaService, times(1)).resonate(USER, 4001L, ResonanceType.EMPATHY);
    }

    @Test
    void authzCandidate001_capturesSignalArgumentsWithoutOwnerConclusion() throws Exception {
        when(resonanceSignalService.sendSignal(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(null);

        mockMvc.perform(authenticated(post("/api/plaza/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":\"" + OTHER_USER
                                + "\",\"cardId\":4001,\"message\":\"" + SIGNAL_MESSAGE + "\"}"), USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        ArgumentCaptor<String> fromCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> cardCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(resonanceSignalService, times(1)).sendSignal(
                fromCaptor.capture(), toCaptor.capture(), cardCaptor.capture(), messageCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(USER);
        assertThat(toCaptor.getValue()).isEqualTo(OTHER_USER);
        assertThat(cardCaptor.getValue()).isEqualTo(4001L);
        assertThat(messageCaptor.getValue()).isEqualTo(SIGNAL_MESSAGE);
    }

    @Test
    void retainsIndependentRouteStatisticsContract() throws Exception {
        var mappings = AuthzCoverageContractTest.scanMappings();

        assertThat(mappings).hasSize(163);
        assertThat(mappings.stream()
                .filter(mapping -> AuthzCoverageContractTest.WRITE_METHODS.contains(mapping.httpMethod()))
                .count()).isEqualTo(93);
        assertThat(mappings.stream()
                .filter(mapping -> !AuthzCoverageContractTest.WRITE_METHODS.contains(mapping.httpMethod()))
                .count()).isEqualTo(70);
    }

    private void saveFixtureUser(String userId, int permissionLevel) {
        User existing = userRepository.findByUserId(userId);
        if (existing == null) {
            userRepository.save(User.builder()
                    .userId(userId)
                    .userName(userId)
                    .password("fixture-password-authz")
                    .permissionLevel(permissionLevel)
                    .build());
        }
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, String userId) {
        return request.header("Authorization", "Bearer " + jwtUtils.generateAccessToken(userId));
    }

    private void expectBusinessForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    private void expectAuthorizationForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    private void expectTokenMissing(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_MISSING.getCode()));
    }
}
