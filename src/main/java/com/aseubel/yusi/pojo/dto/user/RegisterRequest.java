package com.aseubel.yusi.pojo.dto.user;

import com.aseubel.yusi.pojo.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Aseubel
 * @date 2025/5/7 上午10:00
 */
@Data
public class RegisterRequest implements Serializable {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 20, message = "用户名长度必须在2-20个字符之间")
    private String userName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度必须在8-20个字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[^\\s]{8,20}$", message = "密码必须包含大小写字母和数字")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    public User converToUser() {
        return User.builder()
                .userName(userName)
                .password(password)
                .email(email)
                .build();
    }
}
