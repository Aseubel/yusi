package com.aseubel.yusi.config;

import com.aseubel.yusi.common.web.RuntimeOriginHandshakeInterceptor;
import com.aseubel.yusi.controller.DiaryVoiceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class DiaryVoiceWebSocketConfig implements WebSocketConfigurer {

    private final DiaryVoiceWebSocketHandler handler;
    private final RuntimeOriginHandshakeInterceptor originHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, DiaryVoiceWebSocketHandler.ENDPOINT)
                .addInterceptors(originHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
