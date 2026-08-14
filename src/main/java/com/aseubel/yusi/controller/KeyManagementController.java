package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.key.DiaryReEncryptRequest;
import com.aseubel.yusi.pojo.dto.key.KeyModeUpdateRequest;
import com.aseubel.yusi.pojo.dto.key.KeyRecoveryRequest;
import com.aseubel.yusi.pojo.dto.key.KeyRecoveryResponse;
import com.aseubel.yusi.pojo.dto.key.KeySettingsResponse;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.service.key.KeyManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 密钥管理控制器
 * 提供密钥设置查询、更新、密钥更换等功能
 */
@Auth
@Slf4j
@RestController
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
     * Sends a second-factor code to the authenticated user's bound email.
     */
    @RateLimiter(key = "key-recovery-code", time = 60, count = 3, limitType = LimitType.IP)
    @PostMapping("/recovery/send-code")
    public Response<String> sendRecoveryCode() {
        return Response.success(keyManagementService.sendRecoveryCode(UserContext.getUserId()));
    }

    /**
     * Exchanges a verified code for an old key encrypted to a one-time browser key.
     */
    @RateLimiter(key = "key-recovery", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/recovery")
    public Response<KeyRecoveryResponse> recoverKey(@Valid @RequestBody KeyRecoveryRequest request) {
        KeyRecoveryResponse response = keyManagementService.recoverKey(
                UserContext.getUserId(), request.getCode(), request.getRecoveryPublicKey());
        return Response.success(response);
    }

}
