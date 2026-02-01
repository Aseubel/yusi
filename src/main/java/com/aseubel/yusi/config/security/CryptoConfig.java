package com.aseubel.yusi.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

    @Bean
    public CryptoService cryptoService(CryptoProperties properties) {
        return new CryptoService(properties);
    }
}

