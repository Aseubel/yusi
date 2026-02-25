package com.aseubel.yusi.service.developer;

import com.aseubel.yusi.pojo.dto.developer.DeveloperConfigVO;

public interface DeveloperConfigService {

    /**
     * 获取指定用户的开发者配置（API Key等）
     */
    DeveloperConfigVO getConfig(String userId);

    /**
     * 为指定用户生成或刷新 API Key
     */
    DeveloperConfigVO rotateApiKey(String userId);
}
