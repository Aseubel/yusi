package com.aseubel.yusi.service;

import com.aseubel.yusi.pojo.dto.AuthResponse;
import com.aseubel.yusi.pojo.entity.User;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:50
 */
public interface UserService {

    User register(User user);

    AuthResponse login(String userName, String password);

    void logout(String userId, String accessToken);

    User getUserByUserId(String userId);
}
