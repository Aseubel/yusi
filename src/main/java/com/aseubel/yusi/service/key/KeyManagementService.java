package com.aseubel.yusi.service.key;

import com.aseubel.yusi.pojo.dto.key.DiaryReEncryptRequest;
import com.aseubel.yusi.pojo.dto.key.KeyModeUpdateRequest;
import com.aseubel.yusi.pojo.dto.key.KeySettingsResponse;
import com.aseubel.yusi.pojo.entity.Diary;

import java.util.List;

/**
 * 密钥管理服务接口
 */
public interface KeyManagementService {

    /**
     * 获取用户当前密钥设置
     * 
     * @param userId 用户ID
     * @return 密钥设置信息
     */
    KeySettingsResponse getKeySettings(String userId);

    /**
     * 更新用户密钥模式
     * 
     * @param userId  用户ID
     * @param request 更新请求
     */
    void updateKeyMode(String userId, KeyModeUpdateRequest request);

    /**
     * 获取用户所有日记（用于密钥更换时前端解密）
     * 
     * @param userId 用户ID
     * @return 日记列表（包含加密内容）
     */
    List<Diary> getAllDiariesForReEncrypt(String userId);

    /**
     * 批量更新重新加密后的日记
     * 
     * @param userId  用户ID
     * @param request 重新加密请求
     */
    void batchUpdateReEncryptedDiaries(String userId, DiaryReEncryptRequest request);

    /**
     * 验证用户是否有权访问备份密钥（管理员功能）
     * 
     * @param adminUserId  管理员用户ID
     * @param targetUserId 目标用户ID
     * @return 加密的备份密钥
     */
    String getBackupKeyForRecovery(String adminUserId, String targetUserId);
}
