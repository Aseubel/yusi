package com.aseubel.yusi.service.user;

import com.aseubel.yusi.pojo.entity.UserPersona;

public interface UserPersonaService {
    
    /**
     * 获取用户画像/偏好
     * @param userId 用户ID
     * @return UserPersona (如果不存在则返回空对象或默认值)
     */
    UserPersona getUserPersona(String userId);

    /**
     * 更新用户画像/偏好
     * @param userId 用户ID
     * @param persona 更新内容
     * @return 更新后的对象
     */
    UserPersona updateUserPersona(String userId, UserPersona persona);
}
