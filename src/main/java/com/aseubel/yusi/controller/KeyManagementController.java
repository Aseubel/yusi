package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.dto.key.DiaryReEncryptRequest;
import com.aseubel.yusi.pojo.dto.key.KeyModeUpdateRequest;
import com.aseubel.yusi.pojo.dto.key.KeySettingsResponse;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.service.key.KeyManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 密钥管理控制器
 * 提供密钥设置查询、更新、密钥更换等功能
 */
@Auth
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/key")
public class KeyManagementController {

    @Autowired
    private KeyManagementService keyManagementService;

    /**
     * 获取当前用户的密钥设置
     */
    @GetMapping("/settings")
    public Response<KeySettingsResponse> getKeySettings() {
        String userId = UserContext.getUserId();
        KeySettingsResponse settings = keyManagementService.getKeySettings(userId);
        return Response.success(settings);
    }

    /**
     * 更新密钥模式（不涉及日记重新加密）
     * 注意：仅用于新用户首次设置，或者不带日记的模式切换
     */
    @PostMapping("/settings")
    public Response<Void> updateKeyMode(@RequestBody KeyModeUpdateRequest request) {
        String userId = UserContext.getUserId();
        keyManagementService.updateKeyMode(userId, request);
        return Response.success();
    }

    /**
     * 获取所有日记用于重新加密
     * 密钥更换流程第1步：前端调用此接口获取所有日记
     */
    @GetMapping("/diaries-for-reencrypt")
    public Response<List<Diary>> getDiariesForReEncrypt() {
        String userId = UserContext.getUserId();
        List<Diary> diaries = keyManagementService.getAllDiariesForReEncrypt(userId);
        return Response.success(diaries);
    }

    /**
     * 批量更新重新加密后的日记
     * 密钥更换流程第2步：前端使用旧密钥解密、新密钥加密后，调用此接口批量更新
     */
    @PostMapping("/reencrypt-diaries")
    public Response<Void> batchUpdateReEncryptedDiaries(@RequestBody DiaryReEncryptRequest request) {
        String userId = UserContext.getUserId();
        keyManagementService.batchUpdateReEncryptedDiaries(userId, request);
        return Response.success();
    }

    /**
     * 管理员获取用户备份密钥（用于密钥找回）
     */
    @GetMapping("/admin/backup-key/{targetUserId}")
    public Response<String> getBackupKey(@PathVariable("targetUserId") String targetUserId) {
        String adminUserId = UserContext.getUserId();
        String encryptedKey = keyManagementService.getBackupKeyForRecovery(adminUserId, targetUserId);
        return Response.success(encryptedKey);
    }
}
