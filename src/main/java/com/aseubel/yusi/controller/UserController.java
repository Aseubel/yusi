package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import jakarta.validation.Valid;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.user.AuthResponse;
import com.aseubel.yusi.pojo.dto.user.ForgotPasswordRequest;
import com.aseubel.yusi.pojo.dto.user.ResetPasswordRequest;
import com.aseubel.yusi.pojo.dto.user.LoginRequest;

import com.aseubel.yusi.pojo.dto.user.RegisterRequest;
import com.aseubel.yusi.pojo.dto.user.SendRegisterCodeRequest;
import com.aseubel.yusi.pojo.dto.user.UpdateUserRequest;
import com.aseubel.yusi.pojo.dto.user.UserResponse;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:52
 */
@Auth
@Slf4j
@RestController()
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Auth(required = false)
    @RateLimiter(key = "user-register", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/register")
    public Response<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = User.builder()
                .userName(request.getUserName())
                .password(request.getPassword())
                .email(request.getEmail())
                .build();
        return Response.success(UserResponse.from(userService.register(user, request.getCode())));
    }

    @Auth(required = false)
    @RateLimiter(key = "register-code", time = 60, count = 3, limitType = LimitType.IP)
    @PostMapping("/register/send-code")
    public Response<Void> sendRegisterCode(@Valid @RequestBody SendRegisterCodeRequest request) {
        userService.sendRegisterCode(request.getEmail());
        return Response.success();
    }

    @Auth(required = false)
    @RateLimiter(key = "login", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/login")
    public Response<AuthResponse> login(@RequestBody LoginRequest request) {
        return Response.success(userService.login(request.getUserName(), request.getPassword()));
    }

    @Auth(required = false)
    @RateLimiter(key = "refresh", time = 60, count = 30, limitType = LimitType.IP)
    @PostMapping("/refresh")
    public Response<AuthResponse> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken,
            @RequestHeader(value = "X-Old-Access-Token", required = false) String oldAccessToken) {
        return Response.success(userService.refreshToken(refreshToken, oldAccessToken));
    }

    @Auth(required = false)
    @RateLimiter(key = "forgot-password-code", time = 60, count = 3, limitType = LimitType.IP)
    @PostMapping("/forgot-password/send-code")
    public Response<String> sendForgotPasswordCode(@Valid @RequestBody ForgotPasswordRequest request) {
        String maskedEmail = userService.sendForgotPasswordCode(request.getUserName());
        return Response.success(maskedEmail);
    }

    @Auth(required = false)
    @RateLimiter(key = "forgot-password-reset", time = 60, count = 10, limitType = LimitType.IP)
    @PostMapping("/forgot-password/reset")
    public Response<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request.getUserName(), request.getCode(), request.getNewPassword());
        return Response.success();
    }

    @PostMapping("/update")
    public Response<UserResponse> update(@Valid @RequestBody UpdateUserRequest request) {
        String userId = UserContext.getUserId();
        return Response.success(UserResponse.from(
                userService.updateUser(userId, request.getUserName(), request.getEmail())));
    }

    @PostMapping("/logout")
    public Response<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
            userService.logout(UserContext.getUserId(), token);
        }
        return Response.success();
    }
}
