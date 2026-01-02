package com.aseubel.yusi.service.user;

import com.aseubel.yusi.pojo.dto.AuthResponse;
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

    void logout(String userId, String accessToken);

    User getUserByUserId(String userId);

    User updateMatchSettings(String userId, boolean enabled, String intent);

    List<User> getMatchEnabledUsers();
}
