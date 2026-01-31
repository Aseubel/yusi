package com.aseubel.yusi.common.auth;

import com.aseubel.yusi.common.exception.AuthorizationException;
import com.aseubel.yusi.common.exception.ErrorCode;
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
            throw new AuthorizationException(ErrorCode.TOKEN_MISSING);
        }

        if (tokenService.isBlacklisted(token)) {
            throw new AuthorizationException(ErrorCode.TOKEN_INVALID, "令牌已失效");
        }

        try {
            Claims claims = jwtUtils.getClaims(token);
            String userId = claims.getSubject();
            if (!StringUtils.hasText(userId)) {
                userId = (String) claims.get("userId");
                if (!StringUtils.hasText(userId)) {
                    throw new AuthorizationException(ErrorCode.TOKEN_INVALID);
                }
            }
            String username = (String) claims.get("username");
            UserContext.setUserId(userId);
            UserContext.setUsername(username);
        } catch (ExpiredJwtException e) {
            throw new AuthorizationException(ErrorCode.TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new AuthorizationException(ErrorCode.TOKEN_INVALID);
        }

        try {
            return joinPoint.proceed();
        } finally {
            UserContext.clear();
        }
    }
}
