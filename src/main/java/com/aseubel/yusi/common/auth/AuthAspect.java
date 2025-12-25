package com.aseubel.yusi.common.auth;

import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(1)
@Slf4j
public class AuthAspect {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Around("@within(com.aseubel.yusi.common.auth.Auth) || @annotation(com.aseubel.yusi.common.auth.Auth)")
    public Object handleAuth(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auth auth = method.getAnnotation(Auth.class);
        if (auth == null) {
            auth = joinPoint.getTarget().getClass().getAnnotation(Auth.class);
        }

        // If auth is not required, proceed
        if (auth != null && !auth.required()) {
            return joinPoint.proceed();
        }

        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            throw new RuntimeException("未登录");
        }

        if (tokenService.isBlacklisted(token)) {
            throw new RuntimeException("令牌已失效");
        }

        try {
            Claims claims = jwtUtils.getClaims(token);
            String userId = claims.getSubject();
            String username = (String) claims.get("username");
            UserContext.setUserId(userId);
            UserContext.setUsername(username);
        } catch (ExpiredJwtException e) {
            // Token expired, try refresh
            String refreshToken = request.getHeader("X-Refresh-Token");
            if (!StringUtils.hasText(refreshToken)) {
                throw new RuntimeException("登录已过期");
            }

            // Validate refresh token
            try {
                Claims refreshClaims = jwtUtils.getClaims(refreshToken);
                String userId = refreshClaims.getSubject();
                String type = (String) refreshClaims.get("type");
                
                if (!"refresh".equals(type)) {
                    throw new RuntimeException("无效的刷新令牌");
                }

                // Check if refresh token matches stored one
                String storedRefreshToken = tokenService.getRefreshToken(userId);
                if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
                    throw new RuntimeException("刷新令牌已失效");
                }

                // Generate new access token
                User user = userService.getUserByUserId(userId);
                if (user == null) {
                    throw new RuntimeException("用户不存在");
                }
                
                String newAccessToken = jwtUtils.generateAccessToken(userId, user.getUserName());
                
                // Add to response header
                if (response != null) {
                    response.setHeader("X-New-Access-Token", newAccessToken);
                    response.setHeader("Access-Control-Expose-Headers", "X-New-Access-Token");
                }

                UserContext.setUserId(userId);
                UserContext.setUsername(user.getUserName());
            } catch (Exception ex) {
                throw new RuntimeException("登录已过期，请重新登录");
            }
        } catch (Exception e) {
            throw new RuntimeException("无效的令牌");
        }

        try {
            return joinPoint.proceed();
        } finally {
            UserContext.clear();
        }
    }
}
