package com.aseubel.yusi.service.key;

import com.aseubel.yusi.pojo.dto.key.DiaryReEncryptRequest;
import com.aseubel.yusi.pojo.dto.key.KeyModeUpdateRequest;
import com.aseubel.yusi.pojo.dto.key.KeyRecoveryResponse;
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
     * Sends a recovery code to the current user's bound email.
     *
     * @param userId current authenticated user
     * @return masked bound email
     */
    String sendRecoveryCode(String userId);

    /**
     * Verifies the code and encrypts the stored client key for a browser key.
     *
     * @param userId current authenticated user
     * @param code email verification code
     * @param recoveryPublicKey browser-generated RSA-OAEP public key
     * @return encrypted old key and its current salt
     */
    KeyRecoveryResponse recoverKey(String userId, String code, String recoveryPublicKey);

}
