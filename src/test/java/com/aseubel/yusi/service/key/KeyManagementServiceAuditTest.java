package com.aseubel.yusi.service.key;

import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditDetailKeys;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditOperation;
import com.aseubel.yusi.pojo.constant.SecurityAuditReasonCode;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.key.impl.KeyManagementServiceImpl;
import com.aseubel.yusi.service.security.SecurityAuditService;
import com.aseubel.yusi.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyManagementServiceAuditTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private CryptoService cryptoService;
    @Mock
    private UserService userService;
    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private KeyManagementServiceImpl keyManagementService;

    @Test
    void successfulBackupKeyRecoveryCreatesRedactedAudit() {
        User target = User.builder()
                .userId("user-1")
                .hasCloudBackup(true)
                .encryptedBackupKey("encrypted-secret")
                .build();
        when(userService.checkAdmin("admin-1")).thenReturn(true);
        when(userRepository.findByUserId("user-1")).thenReturn(target);

        String result = keyManagementService.getBackupKeyForRecovery("admin-1", "user-1");

        assertEquals("encrypted-secret", result);
        ArgumentCaptor<Map<String, String>> detailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(securityAuditService).recordAdmin(
                eq(SecurityAuditAction.BACKUP_KEY_ACCESSED),
                eq("admin-1"),
                eq("user-1"),
                eq(SecurityAuditResourceType.USER_BACKUP_KEY),
                eq("user-1"),
                eq(SecurityAuditOutcome.SUCCESS),
                eq(SecurityAuditReasonCode.SENSITIVE_ACCESS),
                detailsCaptor.capture());
        assertThat(detailsCaptor.getValue())
                .containsEntry(SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.READ.name())
                .doesNotContainValue("encrypted-secret");
    }
}
