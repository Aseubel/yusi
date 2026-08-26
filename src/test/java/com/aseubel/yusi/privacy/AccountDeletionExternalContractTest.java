package com.aseubel.yusi.privacy;

import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.redis.service.IRedisService;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.privacy.AccountDeletionInventory;
import com.aseubel.yusi.service.privacy.DefaultAccountDeletionExternalPort;
import com.aseubel.yusi.service.user.TokenService;
import com.aseubel.yusi.service.user.impl.AdminServiceImpl;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@DisplayName("mock-contract-only account deletion external boundary")
class AccountDeletionExternalContractTest {

    private static final String TARGET_USER = "fixture-user-delete-target";
    private static final String ADMIN_USER = "fixture-user-delete-admin";
    private static final Set<String> REQUIRED_COLLECTIONS = Set.of(
            "yusi_embedding_collection", "yusi_mid_term_memory", "yusi_match_profile");
    private IRedisService redisService;
    private TokenService tokenService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void currentDeregisterEntryMustDeleteAllThreeMilvusCollections() {
        MilvusClientV2 milvusClient = mock(MilvusClientV2.class);
        List<DeleteReq> requests = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return null;
        }).when(milvusClient).delete(any(DeleteReq.class));

        newAdminService(milvusClient).deregisterUser(TARGET_USER);

        Set<String> actualCollections = new HashSet<>();
        for (DeleteReq request : requests) {
            actualCollections.add(request.getCollectionName());
        }
        assertEquals(REQUIRED_COLLECTIONS, actualCollections,
                "mock-contract-only must cover all three Milvus collections");
    }

    @Test
    void currentDeregisterImplementationMustDeclareRedisAndOssCleanup() throws Exception {
        String source = implementationSource();

        assertAll("mock-contract-only external cleanup declarations",
                () -> assertTrue(source.contains("removeFromMap"),
                        "usage hash field cleanup is missing"),
                () -> assertTrue(source.contains("yusi:violation:count:"),
                        "violation key cleanup is missing"),
                () -> assertTrue(source.contains("deleteOwnedAudioObject"),
                        "audio object cleanup is missing"),
                () -> assertTrue(source.contains("deleteOwnedImages"),
                        "image/attachment object inventory cleanup is missing"));
    }

    @Test
    void accountDeletionObjectCleanupMustRespectOssBatchLimit() throws Exception {
        String source = implementationSource();

        assertTrue(source.contains("MAX_OBJECT_DELETE_BATCH_SIZE"),
                "account deletion must define an explicit OSS object batch limit");
        assertTrue(source.contains("subList"),
                "account deletion must split large OSS inventories into bounded calls");
    }

    @Test
    void accountDeletionMustRemoveOwnedChunkObjectsBeforeClearingSessionKeys() throws Exception {
        IRedisService redis = mock(IRedisService.class);
        TokenService tokens = mock(TokenService.class);
        com.aseubel.yusi.service.oss.OssService oss = mock(com.aseubel.yusi.service.oss.OssService.class);
        String totalKey = "yusi:chunk:" + TARGET_USER + ":fixture-file-digest:totalChunks";
        when(redis.<String>getValue(totalKey)).thenReturn("2");
        when(redis.<String>getValue("yusi:chunk:" + TARGET_USER + ":fixture-file-digest:0"))
                .thenReturn("fixture-chunk-object-a");
        when(redis.<String>getValue("yusi:chunk:" + TARGET_USER + ":fixture-file-digest:1"))
                .thenReturn("fixture-chunk-object-b");

        AccountDeletionInventory inventory = new AccountDeletionInventory(TARGET_USER);
        var addExactKey = AccountDeletionInventory.class.getDeclaredMethod("addExactRedisKey", String.class);
        addExactKey.setAccessible(true);
        addExactKey.invoke(inventory, totalKey);

        new DefaultAccountDeletionExternalPort(mock(MilvusClientV2.class), redis, tokens, oss)
                .deleteObjects(inventory);

        verify(oss, times(1)).deleteOwnedChunkObject("fixture-chunk-object-a", TARGET_USER);
        verify(oss, times(1)).deleteOwnedChunkObject("fixture-chunk-object-b", TARGET_USER);
        verify(oss, times(1)).deleteOwnedImagePrefix(TARGET_USER);
        verify(oss, times(1)).deleteOwnedAudioPrefix(TARGET_USER);
        verify(oss, times(1)).deleteOwnedChunkPrefix(TARGET_USER);
    }

    @Test
    void currentDeregisterImplementationMustNotReportSuccessAfterSwallowedFailure() throws Exception {
        String source = implementationSource();

        assertFalse(source.contains("Successfully deregistered user"),
                "failure-closed deletion must not keep the old success log");
        assertFalse(source.contains("String[] deleteQueries"),
                "failure-closed deletion must not keep the old per-statement swallowing loop");
    }

    @Test
    void currentDeregisterEntryMustIssueExactRedisFamilyCleanup() {
        newAdminService(mock(MilvusClientV2.class)).deregisterUser(TARGET_USER);

        verify(tokenService).deleteRefreshToken(TARGET_USER);
        verify(tokenService).removeAllDeviceTokens(TARGET_USER);
        verify(redisService).remove("yusi:langchain:" + TARGET_USER);
        verify(redisService).remove("yusi:violation:count:" + TARGET_USER);
        verify(redisService).removeUsageFields(TARGET_USER);
        verify(redisService).remove("yusi:user:data:" + TARGET_USER);
        verify(redisService).remove("yusi:user:admin:" + TARGET_USER);
    }

    @Test
    void currentDeregisterEntryMustClearUserDiaryListCacheVariants() {
        newAdminService(mock(MilvusClientV2.class)).deregisterUser(TARGET_USER);

        verify(redisService).removeByPattern("yusi:diary:list:v4:" + TARGET_USER + ":*");
    }

    @Test
    void currentDeregisterEntryMustClearUserNotificationCacheVariants() {
        newAdminService(mock(MilvusClientV2.class)).deregisterUser(TARGET_USER);

        verify(redisService).removeByPattern("yusi:notifications:user:" + TARGET_USER + ":*");
    }

    @Test
    void currentDeregisterEntryMustClearUserMatchCacheVariants() {
        newAdminService(mock(MilvusClientV2.class)).deregisterUser(TARGET_USER);

        verify(redisService).removeByPattern("yusi:match:list:" + TARGET_USER + ":*");
        verify(redisService).remove("yusi:match:status:" + TARGET_USER);
    }

    @Test
    void currentDeregisterEntryMustClearUserPlazaCacheVariants() {
        newAdminService(mock(MilvusClientV2.class)).deregisterUser(TARGET_USER);

        verify(redisService).removeByPattern("yusi:plaza:my:" + TARGET_USER + ":*");
    }

    private AdminServiceImpl newAdminService(MilvusClientV2 milvusClient) {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByUserId(TARGET_USER))
                .thenReturn(User.builder().userId(TARGET_USER).permissionLevel(0).build());
        when(userRepository.findByUserId(ADMIN_USER))
                .thenReturn(User.builder().userId(ADMIN_USER).permissionLevel(10).build());
        SituationRoomRepository situationRoomRepository = mock(SituationRoomRepository.class);
        when(situationRoomRepository.findByMembersContainingOrderByCreatedAtDesc(TARGET_USER))
                .thenReturn(List.of());
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        tokenService = mock(TokenService.class);
        redisService = mock(IRedisService.class);
        UserContext.setUserId(ADMIN_USER);
        return new AdminServiceImpl(
                userRepository,
                mock(DiaryRepository.class),
                situationRoomRepository,
                mock(SituationScenarioRepository.class),
                mock(SuggestionRepository.class),
                mock(InterfaceDailyUsageRepository.class),
                jdbcTemplate,
                tokenService,
                redisService,
                milvusClient,
                mock(SecurityAuditService.class));
    }

    private String implementationSource() throws Exception {
        StringBuilder source = new StringBuilder(Files.readString(Path.of(
                "src/main/java/com/aseubel/yusi/service/user/impl/AdminServiceImpl.java")));
        Path privacyRoot = Path.of("src/main/java/com/aseubel/yusi/service/privacy");
        if (Files.isDirectory(privacyRoot)) {
            try (var paths = Files.walk(privacyRoot)) {
                for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                    source.append('\n').append(Files.readString(path));
                }
            }
        }
        return source.toString();
    }
}
