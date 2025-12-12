package com.aseubel.yusi.service.impl;

import cn.hutool.crypto.digest.BCrypt;
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
        // Check if user exists
        User existingUser = userRepository.findByUserName(user.getUserName());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }
        
        user.generateUserId();
        // Encrypt password
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hashedPassword);
        
        return userRepository.save(user);
    }

    @Override
    public User login(String userName, String password) {
        User user = userRepository.findByUserName(userName);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        
        return user;
    }
}
