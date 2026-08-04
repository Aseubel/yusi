package com.aseubel.yusi.pojo.dto.user;

import com.aseubel.yusi.pojo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Public user representation. Authentication and key material are never exposed. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String userId;
    private String userName;
    private String email;
    private Boolean isMatchEnabled;
    private String matchIntent;
    private String keyMode;
    private Boolean hasCloudBackup;
    private Boolean isAdmin;
    private Boolean isSuperAdmin;

    public static UserResponse from(User user) {
        if (user == null) {
            return null;
        }
        int permissionLevel = user.getPermissionLevel() == null ? 0 : user.getPermissionLevel();
        return UserResponse.builder()
                .userId(user.getUserId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .isMatchEnabled(user.getIsMatchEnabled())
                .matchIntent(user.getMatchIntent())
                .keyMode(user.getKeyMode())
                .hasCloudBackup(user.getHasCloudBackup())
                .isAdmin(permissionLevel >= 10)
                .isSuperAdmin(permissionLevel >= 99)
                .build();
    }
}
