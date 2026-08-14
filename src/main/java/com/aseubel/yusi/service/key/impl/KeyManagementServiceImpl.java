package com.aseubel.yusi.service.key.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.AesGcmCryptoUtils;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.pojo.dto.key.DiaryReEncryptRequest;
import com.aseubel.yusi.pojo.dto.key.KeyModeUpdateRequest;
import com.aseubel.yusi.pojo.dto.key.KeyRecoveryResponse;
import com.aseubel.yusi.pojo.dto.key.KeySettingsResponse;
import com.aseubel.yusi.pojo.constant.KeyMode;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.key.KeyManagementService;
import com.aseubel.yusi.service.diary.DiaryService;
import com.aseubel.yusi.service.user.VerificationCodeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 密钥管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyManagementServiceImpl implements KeyManagementService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final CryptoService cryptoService;
    private final VerificationCodeService verificationCodeService;

    @Autowired
    @Lazy
    private DiaryService diaryService;

    @Override
    public KeySettingsResponse getKeySettings(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        KeySettingsResponse.KeySettingsResponseBuilder builder = KeySettingsResponse.builder()
                .keyMode(user.getKeyMode() != null ? user.getKeyMode() : KeyMode.DEFAULT.code())
                .hasCloudBackup(user.getHasCloudBackup() != null ? user.getHasCloudBackup() : false)
                .backupPublicKey(cryptoService.backupPublicKeySpkiBase64());

        if (KeyMode.CUSTOM.code().equals(user.getKeyMode())) {
            builder.keySalt(user.getKeySalt());
        }

        return builder.build();
    }

    @Override
    @Transactional
    public void updateKeyMode(String userId, KeyModeUpdateRequest request) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        String newMode = request.getKeyMode();
        if (!KeyMode.DEFAULT.code().equals(newMode) && !KeyMode.CUSTOM.code().equals(newMode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的密钥模式");
        }

        user.setKeyMode(newMode);

        if (KeyMode.CUSTOM.code().equals(newMode)) {
            // 自定义密钥模式
            user.setKeySalt(request.getKeySalt());
            user.setHasCloudBackup(request.getEnableCloudBackup() != null ? request.getEnableCloudBackup() : false);

            if (Boolean.TRUE.equals(request.getEnableCloudBackup())) {
                if (request.getEncryptedBackupKey() == null || request.getEncryptedBackupKey().isEmpty()) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "开启云端备份时必须提供加密后的密钥");
                }
                user.setEncryptedBackupKey(request.getEncryptedBackupKey());
            } else {
                user.setEncryptedBackupKey(null);
            }
        } else {
            // 默认密钥模式
            user.setHasCloudBackup(false);
            user.setEncryptedBackupKey(null);
            user.setKeySalt(null);
        }

        userRepository.save(user);
        log.info("User {} updated key mode to {}", userId, newMode);
    }

    @Override
    public List<Diary> getAllDiariesForReEncrypt(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        List<Diary> diaries = diaryRepository.findAllByUserId(userId);
        String keyMode = user.getKeyMode();
        if (keyMode == null || KeyMode.DEFAULT.code().equals(keyMode)) {
            byte[] serverKey = cryptoService.serverAesKeyBytes();
            diaries.forEach(d -> {
                if (d != null && d.getContent() != null && !Boolean.TRUE.equals(d.getClientEncrypted())) {
                    d.setContent(AesGcmCryptoUtils.decryptText(d.getContent(), serverKey));
                }
            });
        }
        return diaries;
    }

    @Override
    @Transactional
    public void batchUpdateReEncryptedDiaries(String userId, DiaryReEncryptRequest request) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        List<Diary> existingDiaries = diaryRepository.findAllByUserId(userId);
        Map<String, Diary> diaryMap = existingDiaries.stream()
                .collect(Collectors.toMap(Diary::getDiaryId, d -> d));

        List<Diary> toUpdate = new ArrayList<>();

        for (DiaryReEncryptRequest.ReEncryptedDiary reEncrypted : request.getDiaries()) {
            Diary diary = diaryMap.get(reEncrypted.getDiaryId());
            if (diary == null) {
                log.warn("Diary not found for re-encryption: {}", reEncrypted.getDiaryId());
                continue;
            }

            if (KeyMode.DEFAULT.code().equals(request.getNewKeyMode())) {
                diary.setClientEncrypted(false);
                String plain = reEncrypted.getEncryptedContent();
                diary.setContent(
                        plain == null ? null : AesGcmCryptoUtils.encryptText(plain, cryptoService.serverAesKeyBytes()));
            } else {
                diary.setClientEncrypted(true);
                diary.setContent(reEncrypted.getEncryptedContent());
            }
            if (reEncrypted.getEncryptedTitle() != null) {
                diary.setTitle(reEncrypted.getEncryptedTitle());
            }
            diary.setUpdateTime(LocalDateTime.now());
            toUpdate.add(diary);
        }

        if (!toUpdate.isEmpty()) {
            diaryRepository.saveAll(toUpdate);
            log.info("Re-encrypted {} diaries for user {}", toUpdate.size(), userId);
            
            // clear caches
            diaryService.evictListCache(userId);
            diaryService.evictFootprintsCache(userId);
            for (Diary d : toUpdate) {
                diaryService.evictDiaryCache(d.getDiaryId(), userId);
            }
        }

        user.setKeyMode(request.getNewKeyMode());
        user.setKeySalt(request.getNewKeySalt());
        user.setHasCloudBackup(request.getEnableCloudBackup() != null ? request.getEnableCloudBackup() : false);

        if (Boolean.TRUE.equals(request.getEnableCloudBackup())) {
            user.setEncryptedBackupKey(request.getEncryptedBackupKey());
        } else {
            user.setEncryptedBackupKey(null);
        }

        userRepository.save(user);
    }

    @Override
    public String sendRecoveryCode(String userId) {
        User user = requireRecoverableUser(userId);
        verificationCodeService.sendCode(user.getEmail(), "找回日记密钥");
        return maskEmail(user.getEmail());
    }

    @Override
    public KeyRecoveryResponse recoverKey(String userId, String code, String recoveryPublicKey) {
        User user = requireRecoverableUser(userId);
        if (!verificationCodeService.verifyCode(user.getEmail(), code)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
        }

        byte[] oldKey = cryptoService.decryptBackupKeyBase64(user.getEncryptedBackupKey());
        String encryptedKey = cryptoService.encryptForRecovery(oldKey, recoveryPublicKey);
        return KeyRecoveryResponse.builder()
                .encryptedKey(encryptedKey)
                .keySalt(user.getKeySalt())
                .build();
    }

    private User requireRecoverableUser(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (!KeyMode.CUSTOM.code().equals(user.getKeyMode())
                || !Boolean.TRUE.equals(user.getHasCloudBackup())
                || StrUtil.isBlank(user.getEncryptedBackupKey())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前未开启云端备份，无法通过验证码找回日记密钥");
        }
        if (StrUtil.isBlank(user.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未设置绑定邮箱，无法找回日记密钥");
        }
        return user;
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        int keepChars = Math.min(3, localPart.length());
        return localPart.substring(0, keepChars) + "***" + domain;
    }

}
