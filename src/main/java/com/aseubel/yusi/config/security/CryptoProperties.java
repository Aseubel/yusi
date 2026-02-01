package com.aseubel.yusi.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yusi.security.crypto")
public class CryptoProperties {

    private String serverAesKeyBase64;

    private String backupRsaPublicKeySpkiBase64;

    private String backupRsaPrivateKeyPkcs8Base64;
}

