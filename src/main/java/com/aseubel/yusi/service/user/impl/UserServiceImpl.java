package com.aseubel.yusi.service.user.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.aseubel.yusi.common.utils.JwtUtils;
import com.aseubel.yusi.pojo.dto.AuthResponse;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.user.UserService;
import com.aseubel.yusi.service.user.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

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
            throw new RuntimeException("用户名已存在");
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
            throw new RuntimeException("用户不存在");
        }
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String accessToken = jwtUtils.generateAccessToken(user.getUserId(), user.getUserName());
        String refreshToken = jwtUtils.generateRefreshToken(user.getUserId());
        
        tokenService.saveRefreshToken(user.getUserId(), refreshToken);
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(user)
                .build();
    }

    @Override
    public void logout(String userId, String accessToken) {
        tokenService.deleteRefreshToken(userId);
        tokenService.addToBlacklist(accessToken);
    }

    @Override
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
    public java.util.List<User> getMatchEnabledUsers() {
        return userRepository.findByIsMatchEnabledTrue();
    }
}
