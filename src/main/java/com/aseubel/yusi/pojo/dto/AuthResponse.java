package com.aseubel.yusi.pojo.dto;

import com.aseubel.yusi.pojo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse implements Serializable {
    private String accessToken;
    private String refreshToken;
    private User user;
}
