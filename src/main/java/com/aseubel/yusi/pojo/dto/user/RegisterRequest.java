package com.aseubel.yusi.pojo.dto.user;

import com.aseubel.yusi.pojo.entity.User;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:00
 */
@Data
public class RegisterRequest implements Serializable {

    private String userName;

    private String password;

    private String email;

    public User converToUser() {
        return User.builder()
                .userName(userName)
                .password(password)
                .email(email)
                .build();
    }
}
