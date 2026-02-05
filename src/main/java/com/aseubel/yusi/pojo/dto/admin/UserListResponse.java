package com.aseubel.yusi.pojo.dto.admin;

import com.aseubel.yusi.pojo.entity.User;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserListResponse extends User {
    // Extend User entity or map specific fields if needed
    // For now, returning User entity structure is fine, maybe omit sensitive fields
    // in the future
}
