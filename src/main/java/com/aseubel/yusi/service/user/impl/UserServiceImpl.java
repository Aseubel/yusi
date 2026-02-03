package com.aseubel.yusi.service.user.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;

import com.aseubel.yusi.common.exception.BusinessException;
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

@Service
public class UserServiceImpl implements UserService {

    private static final int MAX_DEVICES = 3;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private TokenService tokenService;

    @Override
    public User register(User user) {
        User existingUser = userRepository.findByUserName(user.getUserName());
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
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
            throw new BusinessException("用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // Enforce device limit before adding new token (max 3 devices)
        tokenService.enforceDeviceLimit(user.getUserId(), MAX_DEVICES);

        String accessToken = jwtUtils.generateAccessToken(user.getUserId(), user.getUserName());
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
        if (!jwtUtils.validateToken(refreshToken)) {
            throw new BusinessException("无效的刷新令牌");
        }

        String type = jwtUtils.getTypeFromToken(refreshToken);
        if (!"refresh".equals(type)) {
            throw new BusinessException("非刷新令牌");
        }

        String userId = jwtUtils.getUserIdFromToken(refreshToken);
        String storedRefreshToken = tokenService.getRefreshToken(userId);

        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new BusinessException("刷新令牌已失效");
        }

        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // Enforce device limit and add new device token
        tokenService.enforceDeviceLimit(userId, MAX_DEVICES);

        String newAccessToken = jwtUtils.generateAccessToken(userId, user.getUserName());
        // Add the new device token for the refreshed session
        tokenService.addDeviceToken(userId, newAccessToken, "refresh");

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
}
