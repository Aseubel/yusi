package com.aseubel.yusi.pojo.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Internal permission information exposed only from an administrator endpoint. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPermissionResponse {
    private Integer permissionLevel;
}
