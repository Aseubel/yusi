package com.aseubel.yusi.config;

import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.service.user.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Shares access-token validation between STOMP and native WebSocket endpoints. */
@Component
@RequiredArgsConstructor
public class WebSocketTokenAuthenticator {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;

    public String authenticate(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("WebSocket token is required");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty() || tokenService.isBlacklisted(token)) {
            throw new IllegalArgumentException("Invalid WebSocket token");
        }
        Claims claims = jwtUtils.getClaims(token);
        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Access token is required");
        }
        String userId = claims.getSubject();
        if (!StringUtils.hasText(userId) || !tokenService.isValidDeviceToken(userId, token)) {
            throw new IllegalArgumentException("Invalid WebSocket token");
        }
        return userId;
    }
}
