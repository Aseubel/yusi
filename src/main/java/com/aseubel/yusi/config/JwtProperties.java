package com.aseubel.yusi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "yusi.jwt")
public class JwtProperties {
    /**
     * Secret key
     */
    private String secret = "yusi-secret-key-should-be-very-long-and-secure-base64-encoded";
    
    /**
     * Access token expiration time in milliseconds (15 minutes)
     */
    private long accessTokenExpiration = 15 * 60 * 1000;
    
    /**
     * Refresh token expiration time in milliseconds (7 days)
     */
    private long refreshTokenExpiration = 7 * 24 * 60 * 60 * 1000;
}
