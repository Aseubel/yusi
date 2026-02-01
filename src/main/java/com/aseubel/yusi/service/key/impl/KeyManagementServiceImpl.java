package com.aseubel.yusi.service.key.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.utils.AesGcmCryptoUtils;
import com.aseubel.yusi.config.security.CryptoService;
import com.aseubel.yusi.pojo.dto.key.DiaryReEncryptRequest;
import com.aseubel.yusi.pojo.dto.key.KeyModeUpdateRequest;
import com.aseubel.yusi.pojo.dto.key.KeySettingsResponse;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.key.KeyManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class KeyManagementServiceImpl implements KeyManagementService {

    private static final String KEY_MODE_DEFAULT = "DEFAULT";
    private static final String KEY_MODE_CUSTOM = "CUSTOM";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private CryptoService cryptoService;

    @Override
    public KeySettingsResponse getKeySettings(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        KeySettingsResponse.KeySettingsResponseBuilder builder = KeySettingsResponse.builder()
                .keyMode(user.getKeyMode() != null ? user.getKeyMode() : KEY_MODE_DEFAULT)
                .hasCloudBackup(user.getHasCloudBackup() != null ? user.getHasCloudBackup() : false)
                .backupPublicKey(cryptoService.backupPublicKeySpkiBase64());

        if (KEY_MODE_CUSTOM.equals(user.getKeyMode())) {
            builder.keySalt(user.getKeySalt());
        }

        return builder.build();
    }

    @Override
    @Transactional
    public void updateKeyMode(String userId, KeyModeUpdateRequest request) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        String newMode = request.getKeyMode();
        if (!KEY_MODE_DEFAULT.equals(newMode) && !KEY_MODE_CUSTOM.equals(newMode)) {
            throw new BusinessException("无效的密钥模式");
        }

        user.setKeyMode(newMode);

        if (KEY_MODE_CUSTOM.equals(newMode)) {
            // 自定义密钥模式
            user.setKeySalt(request.getKeySalt());
            user.setHasCloudBackup(request.getEnableCloudBackup() != null ? request.getEnableCloudBackup() : false);

            if (Boolean.TRUE.equals(request.getEnableCloudBackup())) {
                if (request.getEncryptedBackupKey() == null || request.getEncryptedBackupKey().isEmpty()) {
                    throw new BusinessException("开启云端备份时必须提供加密后的密钥");
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
            throw new BusinessException("用户不存在");
        }

        List<Diary> diaries = diaryRepository.findAllByUserId(userId);
        String keyMode = user.getKeyMode();
        if (keyMode == null || KEY_MODE_DEFAULT.equals(keyMode)) {
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
            throw new BusinessException("用户不存在");
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

            if (KEY_MODE_DEFAULT.equals(request.getNewKeyMode())) {
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
    public String getBackupKeyForRecovery(String adminUserId, String targetUserId) {
        User admin = userRepository.findByUserId(adminUserId);
        if (admin == null || admin.getPermissionLevel() == null || admin.getPermissionLevel() < 10) {
            throw new BusinessException("无权限：仅管理员可访问备份密钥");
        }

        User targetUser = userRepository.findByUserId(targetUserId);
        if (targetUser == null) {
            throw new BusinessException("目标用户不存在");
        }

        if (!Boolean.TRUE.equals(targetUser.getHasCloudBackup())) {
            throw new BusinessException("该用户未开启云端密钥备份");
        }

        if (targetUser.getEncryptedBackupKey() == null) {
            throw new BusinessException("未找到备份密钥");
        }

        log.info("Admin {} accessed backup key for user {}", adminUserId, targetUserId);
        return targetUser.getEncryptedBackupKey();
    }
}
