package com.aseubel.yusi.auth;

import com.aseubel.yusi.common.auth.AuthAspect;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.config.JwtProperties;
import com.aseubel.yusi.service.UserService;
import com.aseubel.yusi.service.auth.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class AuthTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    @InjectMocks
    private AuthAspect authAspect;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        ServletRequestAttributes attributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(attributes);
        
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(this.getClass().getDeclaredMethods()[0]); // Dummy method
        when(joinPoint.getTarget()).thenReturn(this); // Dummy target
    }

    @AfterEach
    void tearDown() throws Exception {
        UserContext.clear();
        RequestContextHolder.resetRequestAttributes();
        closeable.close();
    }

    @Test
    void testAuthSuccess() throws Throwable {
        // Mock token
        String token = "valid_token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.isBlacklisted(token)).thenReturn(false);
        
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user123");
        when(claims.get("username")).thenReturn("testuser");
        when(jwtUtils.getClaims(token)).thenReturn(claims);

        // Mock proceed to verify context
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertEquals("user123", UserContext.getUserId());
            assertEquals("testuser", UserContext.getUsername());
            return null;
        });

        // Call aspect
        authAspect.handleAuth(joinPoint);

        // Verify
        verify(joinPoint).proceed();
    }

    @Test
    void testAuthFail_NoToken() {
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> authAspect.handleAuth(joinPoint));
    }

    @Test
    void testAuthFail_Blacklisted() {
        String token = "blacklisted_token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenService.isBlacklisted(token)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> authAspect.handleAuth(joinPoint));
    }

    @Test
    void testTokenRefresh() throws Throwable {
        String expiredToken = "expired_token";
        String refreshToken = "valid_refresh_token";
        
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);
        when(request.getHeader("X-Refresh-Token")).thenReturn(refreshToken);
        
        when(tokenService.isBlacklisted(expiredToken)).thenReturn(false);
        when(jwtUtils.getClaims(expiredToken)).thenThrow(new ExpiredJwtException(null, null, "Expired"));
        
        Claims refreshClaims = mock(Claims.class);
        when(refreshClaims.getSubject()).thenReturn("user123");
        when(refreshClaims.get("type")).thenReturn("refresh");
        when(jwtUtils.getClaims(refreshToken)).thenReturn(refreshClaims);
        
        when(tokenService.getRefreshToken("user123")).thenReturn(refreshToken);
        
        com.aseubel.yusi.pojo.entity.User user = new com.aseubel.yusi.pojo.entity.User();
        user.setUserId("user123");
        user.setUserName("testuser");
        when(userService.getUserByUserId("user123")).thenReturn(user);
        
        String newAccessToken = "new_access_token";
        when(jwtUtils.generateAccessToken("user123", "testuser")).thenReturn(newAccessToken);

        // Mock proceed to verify context
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            assertEquals("user123", UserContext.getUserId());
            return null;
        });

        // Call aspect
        authAspect.handleAuth(joinPoint);

        // Verify
        verify(response).setHeader("X-New-Access-Token", newAccessToken);
        verify(joinPoint).proceed();
    }
}
