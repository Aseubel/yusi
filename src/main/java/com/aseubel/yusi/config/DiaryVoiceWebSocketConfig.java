package com.aseubel.yusi.config;

import com.aseubel.yusi.controller.DiaryVoiceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DiaryVoiceWebSocketConfig implements WebSocketConfigurer {

    private final DiaryVoiceWebSocketHandler handler;

    @Value("${yusi.web.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = StringUtils.commaDelimitedListToStringArray(allowedOrigin);
        registry.addHandler(handler, DiaryVoiceWebSocketHandler.ENDPOINT)
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
