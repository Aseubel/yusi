package com.aseubel.yusi.service.impl;

import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:50
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public User register(User user) {
        user.generateUserId();
        return userRepository.save(user);
    }
}
