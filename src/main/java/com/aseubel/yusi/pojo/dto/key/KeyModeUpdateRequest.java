package com.aseubel.yusi.pojo.dto.key;

import lombok.Data;

/**
 * 密钥模式更新请求
 */
@Data
public class KeyModeUpdateRequest {

    /**
     * 密钥模式: DEFAULT 或 CUSTOM
     */
    private String keyMode;

    /**
     * 是否开启云端备份（仅CUSTOM模式有效）
     */
    private Boolean enableCloudBackup;

    /**
     * 加密后的备份密钥（使用管理员公钥加密，仅当enableCloudBackup为true时需要）
     */
    private String encryptedBackupKey;

    /**
     * 密钥派生盐值（仅CUSTOM模式需要）
     */
    private String keySalt;
}
