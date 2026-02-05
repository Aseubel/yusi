package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.dto.user.AuthResponse;
import com.aseubel.yusi.pojo.dto.user.LoginRequest;

import com.aseubel.yusi.pojo.dto.user.RegisterRequest;
import com.aseubel.yusi.pojo.dto.user.UpdateUserRequest;
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
@CrossOrigin("*")
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Auth(required = false)
    @PostMapping("/register")
    public Response<User> register(@RequestBody RegisterRequest request) {
        User user = User.builder()
                .userName(request.getUserName())
                .password(request.getPassword())
                .email(request.getEmail())
                .build();
        return Response.success(userService.register(user));
    }

    @Auth(required = false)
    @PostMapping("/login")
    public Response<AuthResponse> login(@RequestBody LoginRequest request) {
        return Response.success(userService.login(request.getUserName(), request.getPassword()));
    }

    @Auth(required = false)
    @PostMapping("/refresh")
    public Response<AuthResponse> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken,
            @RequestHeader(value = "X-Old-Access-Token", required = false) String oldAccessToken) {
        return Response.success(userService.refreshToken(refreshToken, oldAccessToken));
    }

    @PostMapping("/update")
    public Response<User> update(@RequestBody UpdateUserRequest request) {
        String userId = UserContext.getUserId();
        return Response.success(userService.updateUser(userId, request.getUserName(), request.getEmail()));
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
