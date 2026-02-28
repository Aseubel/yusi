package com.aseubel.yusi.service.user.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import lombok.extern.slf4j.Slf4j;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.pojo.dto.user.AuthResponse;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.redis.annotation.QueryCache;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.TokenService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private static final int MAX_DEVICES = 3;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private com.aseubel.yusi.service.user.VerificationCodeService verificationCodeService;

    @Override
    public User register(User user) {
        User existingUser = userRepository.findByUserName(user.getUserName());
        if (existingUser != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已存在");
        }
        
        if (StrUtil.isNotBlank(user.getEmail())) {
            User existingEmail = userRepository.findByEmail(user.getEmail());
            if (existingEmail != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "该邮箱已被注册");
            }
        }

        user.generateUserId();
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hashedPassword);
        return userRepository.save(user);
    }

    @Override
    public AuthResponse login(String userName, String password) {
        User user = userRepository.findByUserName(userName);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码错误");
        }

        // Enforce device limit before adding new token (max 3 devices)
        tokenService.enforceDeviceLimit(user.getUserId(), MAX_DEVICES);

        String accessToken = jwtUtils.generateAccessToken(user.getUserId());
        String refreshToken = jwtUtils.generateRefreshToken(user.getUserId());

        tokenService.saveRefreshToken(user.getUserId(), refreshToken);
        // Add the new device token
        tokenService.addDeviceToken(user.getUserId(), accessToken, "login");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user)
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        return refreshToken(refreshToken, null);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken, String oldAccessToken) {
        log.info("收到刷新token请求, refreshToken: {}, oldAccessToken: {}", refreshToken, oldAccessToken);
        
        if (!jwtUtils.validateToken(refreshToken)) {
            log.warn("刷新token无效: validateToken返回false");
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "无效的刷新令牌");
        }

        String type = jwtUtils.getTypeFromToken(refreshToken);
        log.info("token type: {}", type);
        if (!"refresh".equals(type)) {
            log.warn("token类型不是refresh: {}", type);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "非刷新令牌");
        }

        String userId = jwtUtils.getUserIdFromToken(refreshToken);
        log.info("从token解析出userId: {}", userId);
        String storedRefreshToken = tokenService.getRefreshToken(userId);
        log.info("Redis中存储的refreshToken: {}, 前端传来的refreshToken: {}", storedRefreshToken, refreshToken);

        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            log.warn("刷新令牌已失效: storedRefreshToken={}, refreshToken={}", storedRefreshToken, refreshToken);
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "刷新令牌已失效");
        }

        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        // Remove the old access token from device list if provided
        if (oldAccessToken != null && !oldAccessToken.isEmpty()) {
            tokenService.removeDeviceToken(userId, oldAccessToken);
        }

        // Generate new access token
        String newAccessToken = jwtUtils.generateAccessToken(userId);
        // Add the new token
        tokenService.addDeviceToken(userId, newAccessToken, "refresh");

        // 更新 Redis 中的 refreshToken 过期时间
        tokenService.saveRefreshToken(userId, refreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Return same refresh token
                .user(user)
                .build();
    }

    @Override
    public void logout(String userId, String accessToken) {
        // Remove this specific device token
        tokenService.removeDeviceToken(userId, accessToken);
        tokenService.deleteRefreshToken(userId);
    }

    @Override
    @QueryCache(key = "'user:data:' + #userId")
    public User getUserByUserId(String userId) {
        return userRepository.findByUserId(userId);
    }

    @Override
    public User updateMatchSettings(String userId, boolean enabled, String intent) {
        User user = userRepository.findByUserId(userId);
        if (user != null) {
            user.setIsMatchEnabled(enabled);
            user.setMatchIntent(intent);
            return userRepository.save(user);
        }
        return null;
    }

    @Override
    public List<User> getMatchEnabledUsers() {
        return userRepository.findByIsMatchEnabledTrue();
    }

    @Override
    @QueryCache(key = "'user:admin:' + #userId")
    public Boolean checkAdmin(String userId) {
        if (StrUtil.isEmpty(userId)) {
            return false;
        }
        try {
            User user = this.getUserByUserId(userId);
            return user != null && user.getPermissionLevel() != null && user.getPermissionLevel() >= 10;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public User updateUser(String userId, String userName, String email) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }

        if (StrUtil.isNotEmpty(userName) && !userName.equals(user.getUserName())) {
            User existing = userRepository.findByUserName(userName);
            if (existing != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Username already exists");
            }
            user.setUserName(userName);
        }

        if (StrUtil.isNotEmpty(email)) {
            user.setEmail(email);
        }

        return userRepository.save(user);
    }

    @Override
    public void sendForgotPasswordCode(String userName) {
        User user = userRepository.findByUserName(userName);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (StrUtil.isBlank(user.getEmail())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未设置邮箱或邮箱不可用，可联系管理员解决");
        }
        // Simple email validation
        if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
             throw new BusinessException(ErrorCode.PARAM_ERROR, "邮箱格式不正确，可联系管理员解决");
        }
        verificationCodeService.sendCode(user.getEmail());
    }

    @Override
    public void resetPassword(String userName, String code, String newPassword) {
        User user = userRepository.findByUserName(userName);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (StrUtil.isBlank(user.getEmail())) {
             throw new BusinessException(ErrorCode.PARAM_ERROR, "未设置邮箱，无法重置密码");
        }
        if (!verificationCodeService.verifyCode(user.getEmail(), code)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码错误或已过期");
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userRepository.save(user);
    }
}
