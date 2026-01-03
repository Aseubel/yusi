package com.aseubel.yusi.pojo.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * @author Aseubel
 * @date 2025/12/12
 */
@Data
public class LoginRequest implements Serializable {

    private String userName;

    private String password;
}
