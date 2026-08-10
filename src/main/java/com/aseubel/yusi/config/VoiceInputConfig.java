package com.aseubel.yusi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VoiceInputProperties.class)
public class VoiceInputConfig {
}
