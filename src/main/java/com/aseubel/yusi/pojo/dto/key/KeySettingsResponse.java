package com.aseubel.yusi.pojo.dto.key;

import lombok.Builder;
import lombok.Data;

/**
 * 密钥设置响应
 */
@Data
@Builder
public class KeySettingsResponse {

    /**
     * 当前密钥模式: DEFAULT 或 CUSTOM
     */
    private String keyMode;

    /**
     * 是否已开启云端备份
     */
    private Boolean hasCloudBackup;

    /**
     * 密钥派生盐值（仅CUSTOM模式返回）
     */
    private String keySalt;

    /**
     * 服务端默认密钥（仅DEFAULT模式返回，用于前端加密）
     */
    private String serverKey;
}
