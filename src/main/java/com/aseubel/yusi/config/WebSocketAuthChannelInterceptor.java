package com.aseubel.yusi.config;

import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.service.user.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/** Authenticates STOMP connections and limits subscriptions to related topics. */
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final TokenService tokenService;
    private final SoulMatchRepository soulMatchRepository;
    private final SituationRoomRepository situationRoomRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String token = firstHeader(accessor, "Authorization");
            String userId = authenticate(token);
            accessor.setUser(() -> userId);
            return message;
        }

        Principal principal = accessor.getUser();
        if (principal == null) {
            return null;
        }

        if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            if (!isAllowedDestination(command, accessor.getDestination(), principal.getName())) {
                return null;
            }
        }
        return message;
    }

    private String authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("WebSocket token is required");
        }
        String token = header.substring(7).trim();
        if (token.isEmpty() || tokenService.isBlacklisted(token)) {
            throw new IllegalArgumentException("Invalid WebSocket token");
        }
        Claims claims = jwtUtils.getClaims(token);
        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Access token is required");
        }
        String userId = claims.getSubject();
        if (userId == null || !tokenService.isValidDeviceToken(userId, token)) {
            throw new IllegalArgumentException("Invalid WebSocket token");
        }
        return userId;
    }

    private boolean isAllowedDestination(StompCommand command, String destination, String userId) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        if (command == StompCommand.SEND) {
            return "/app/soul-chat/status".equals(destination);
        }

        String[] parts = destination.split("/");
        if (parts.length == 4 && "topic".equals(parts[1]) && "soul-chat".equals(parts[2])) {
            try {
                Long matchId = Long.valueOf(parts[3]);
                return soulMatchRepository.findById(matchId)
                        .map(match -> userId.equals(match.getUserAId()) || userId.equals(match.getUserBId()))
                        .orElse(false);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (parts.length == 5 && "topic".equals(parts[1]) && "soul-chat".equals(parts[2])
                && "status".equals(parts[3])) {
            try {
                Long matchId = Long.valueOf(parts[4]);
                return soulMatchRepository.findById(matchId)
                        .map(match -> userId.equals(match.getUserAId()) || userId.equals(match.getUserBId()))
                        .orElse(false);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (parts.length >= 4 && parts.length <= 5 && "topic".equals(parts[1])
                && "room".equals(parts[2])
                && (parts.length == 4 || "status".equals(parts[4]))) {
            return situationRoomRepository.findById(parts[3])
                    .map(room -> room.getMembers() != null && room.getMembers().contains(userId))
                    .orElse(false);
        }
        return false;
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
