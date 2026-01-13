package com.aseubel.yusi.pojo.dto.key;

import lombok.Data;
import java.util.List;

/**
 * 日记重新加密请求（密钥更换时使用）
 */
@Data
public class DiaryReEncryptRequest {

    /**
     * 重新加密后的日记列表
     */
    private List<ReEncryptedDiary> diaries;

    /**
     * 新的密钥模式
     */
    private String newKeyMode;

    /**
     * 新的密钥盐值（CUSTOM模式需要）
     */
    private String newKeySalt;

    /**
     * 是否开启云端备份
     */
    private Boolean enableCloudBackup;

    /**
     * 加密后的备份密钥
     */
    private String encryptedBackupKey;

    @Data
    public static class ReEncryptedDiary {
        /**
         * 日记ID
         */
        private String diaryId;

        /**
         * 重新加密后的内容
         */
        private String encryptedContent;

        /**
         * 重新加密后的标题（如果需要加密）
         */
        private String encryptedTitle;
    }
}
