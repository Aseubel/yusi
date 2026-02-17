package com.aseubel.yusi.pojo.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {
    @Size(min = 2, max = 20, message = "用户名长度必须在2-20个字符之间")
    private String userName;

    @Email(message = "邮箱格式不正确")
    private String email;
}
