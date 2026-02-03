package com.aseubel.yusi.pojo.entity;

import com.aseubel.yusi.common.utils.UuidUtils;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

/**
 * @author Aseubel
 * @date 2025/5/7 上午1:04
 */
@Data
@Entity
@Builder
@Table(name = "user")
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "username")
    private String userName;

    @Column(name = "password")
    private String password;

    @Column(name = "email")
    private String email;

    @Column(name = "is_match_enabled")
    private Boolean isMatchEnabled = false;

    @Column(name = "match_intent")
    private String matchIntent;

    @Column(name = "permission_level")
    private Integer permissionLevel = 0;

    /**
     * 密钥模式: DEFAULT(默认服务端密钥) / CUSTOM(用户自定义密钥)
     */
    @Column(name = "key_mode")
    private String keyMode = "DEFAULT";

    /**
     * 是否开启云端密钥备份（仅CUSTOM模式有效）
     */
    @Column(name = "has_cloud_backup")
    private Boolean hasCloudBackup = false;

    /**
     * 云端备份的加密密钥（使用管理员公钥加密）
     */
    @Column(name = "encrypted_backup_key", length = 1024)
    private String encryptedBackupKey;

    /**
     * 密钥派生盐值（用于PBKDF2/Argon2）
     */
    @Column(name = "key_salt")
    private String keySalt;

    public String generateUserId() {
        this.userId = UuidUtils.genUuidSimple();
        return this.userId;
    }
}
