package com.aseubel.yusi.service.user;

import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditActorType;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.InterfaceDailyUsageRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.security.SecurityAuditCommand;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.user.impl.AdminServiceImpl;
import com.aseubel.yusi.service.user.TokenService;
import com.aseubel.yusi.redis.service.IRedisService;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceAuditTest {

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
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private AdminServiceImpl adminService;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void permissionChangesCreateScopedAdministratorAudit() {
        UserContext.setUserId("admin-1");
        User target = User.builder()
                .userId("user-1")
                .permissionLevel(1)
                .build();
        when(userRepository.findByUserId("user-1")).thenReturn(target);

        adminService.updateUserPermission("user-1", 5);

        ArgumentCaptor<java.util.Map<String, String>> captor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(securityAuditService).recordAdmin(
                eq(SecurityAuditAction.ADMIN_PERMISSION_UPDATED),
                eq("admin-1"), eq("user-1"), eq(SecurityAuditResourceType.USER), eq("user-1"),
                eq(SecurityAuditOutcome.SUCCESS), eq("ADMIN_MUTATION"), captor.capture());
        assertThat(captor.getValue()).containsEntry("fromStatus", "1")
                .containsEntry("toStatus", "5");
    }
}
