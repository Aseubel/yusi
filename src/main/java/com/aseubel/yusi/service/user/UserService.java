package com.aseubel.yusi.service.user;

import com.aseubel.yusi.pojo.dto.user.AuthResponse;
import com.aseubel.yusi.pojo.entity.User;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:50
 */
public interface UserService {

    User register(User user);

    AuthResponse login(String userName, String password);

    AuthResponse refreshToken(String refreshToken);

    AuthResponse refreshToken(String refreshToken, String oldAccessToken);

    void logout(String userId, String accessToken);

    User getUserByUserId(String userId);

    User updateMatchSettings(String userId, boolean enabled, String intent);

    List<User> getMatchEnabledUsers();

    Boolean checkAdmin(String userId);

    User updateUser(String userId, String userName, String email);

    /**
     * 发送找回密码验证码
     * @param userName 用户名
     */
    void sendForgotPasswordCode(String userName);

    /**
     * 重置密码
     * @param userName 用户名
     * @param code 验证码
     * @param newPassword 新密码
     */
    void resetPassword(String userName, String code, String newPassword);
}
