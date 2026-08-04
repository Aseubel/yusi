package com.aseubel.yusi.pojo.dto.admin;

import com.aseubel.yusi.pojo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** User representation restricted to authenticated administrator responses. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private Long id;
    private String userId;
    private String userName;
    private String email;
    private Boolean isMatchEnabled;
    private String matchIntent;
    private Integer permissionLevel;

    public static AdminUserResponse from(User user) {
        if (user == null) {
            return null;
        }
        return AdminUserResponse.builder()
                .id(user.getId())
                .userId(user.getUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .isMatchEnabled(user.getIsMatchEnabled())
                .matchIntent(user.getMatchIntent())
                .permissionLevel(user.getPermissionLevel())
                .build();
    }
}
