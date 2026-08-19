package com.aseubel.yusi.config;

import com.aseubel.yusi.observability.trace.TraceIdWebSocketInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.util.StringUtils;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final TraceIdWebSocketInterceptor traceIdWebSocketInterceptor;

    @Value("${yusi.web.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    public WebSocketConfig(WebSocketAuthChannelInterceptor authChannelInterceptor,
            TraceIdWebSocketInterceptor traceIdWebSocketInterceptor) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.traceIdWebSocketInterceptor = traceIdWebSocketInterceptor;
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(traceIdWebSocketInterceptor, authChannelInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 配置消息代理
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        
        config.enableSimpleBroker("/topic")
                .setTaskScheduler(taskScheduler)
                .setHeartbeatValue(new long[]{10000, 10000});
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 配置 WebSocket 端点
        String[] allowedOrigins = StringUtils.commaDelimitedListToStringArray(allowedOrigin);
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
        
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(allowedOrigins);
    }
}
