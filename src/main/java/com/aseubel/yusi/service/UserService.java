package com.aseubel.yusi.service;

import com.aseubel.yusi.pojo.entity.User;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:50
 */
public interface UserService {

    User register(User user);

    User login(String userName, String password);
}
