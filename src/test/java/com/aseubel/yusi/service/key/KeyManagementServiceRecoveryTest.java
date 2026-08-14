package com.aseubel.yusi.service.key;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.pojo.dto.key.KeyRecoveryResponse;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.key.impl.KeyManagementServiceImpl;
import com.aseubel.yusi.service.user.VerificationCodeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class KeyManagementServiceRecoveryTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DiaryRepository diaryRepository = mock(DiaryRepository.class);
    private final CryptoService cryptoService = mock(CryptoService.class);
    private final VerificationCodeService verificationCodeService = mock(VerificationCodeService.class);
    private final KeyManagementServiceImpl service = new KeyManagementServiceImpl(
            userRepository, diaryRepository, cryptoService, verificationCodeService);

    @Test
    void sendsRecoveryCodeToTheCurrentUsersBoundEmail() {
        User user = recoverableUser();
        when(userRepository.findByUserId("user-1")).thenReturn(user);

        String maskedEmail = service.sendRecoveryCode("user-1");

        assertThat(maskedEmail).isEqualTo("ali***@example.com");
        verify(verificationCodeService).sendCode("alice@example.com", "找回日记密钥");
    }

    @Test
    void returnsAKeyEncryptedForTheBrowserAfterCodeVerification() {
        User user = recoverableUser();
        when(userRepository.findByUserId("user-1")).thenReturn(user);
        when(verificationCodeService.verifyCode("alice@example.com", "123456")).thenReturn(true);
        when(cryptoService.decryptBackupKeyBase64("server-encrypted-key"))
                .thenReturn(new byte[]{1, 2, 3});
        when(cryptoService.encryptForRecovery(new byte[]{1, 2, 3}, "browser-public-key"))
                .thenReturn("browser-encrypted-key");

        KeyRecoveryResponse response = service.recoverKey("user-1", "123456", "browser-public-key");

        assertThat(response.getEncryptedKey()).isEqualTo("browser-encrypted-key");
        assertThat(response.getKeySalt()).isEqualTo("current-salt");
        verify(verificationCodeService).verifyCode("alice@example.com", "123456");
    }

    @Test
    void rejectsRecoveryWhenTheUserHasNoCloudBackup() {
        User user = recoverableUser();
        user.setHasCloudBackup(false);
        when(userRepository.findByUserId("user-1")).thenReturn(user);

        assertThatThrownBy(() -> service.recoverKey("user-1", "123456", "browser-public-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("云端备份");

        verifyNoInteractions(verificationCodeService, cryptoService);
    }

    @Test
    void rejectsAnInvalidCodeBeforeDecryptingTheStoredKey() {
        User user = recoverableUser();
        when(userRepository.findByUserId("user-1")).thenReturn(user);
        when(verificationCodeService.verifyCode("alice@example.com", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.recoverKey("user-1", "000000", "browser-public-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码");

        verify(cryptoService, never()).decryptBackupKeyBase64(anyString());
    }

    private User recoverableUser() {
        return User.builder()
                .userId("user-1")
                .email("alice@example.com")
                .keyMode("CUSTOM")
                .hasCloudBackup(true)
                .keySalt("current-salt")
                .encryptedBackupKey("server-encrypted-key")
                .build();
    }
}
