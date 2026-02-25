package com.aseubel.yusi.service.developer.impl;

import com.aseubel.yusi.pojo.dto.developer.DeveloperConfigVO;
import com.aseubel.yusi.pojo.entity.DeveloperConfig;
import com.aseubel.yusi.repository.DeveloperConfigRepository;
import com.aseubel.yusi.service.developer.DeveloperConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperConfigServiceImpl implements DeveloperConfigService {

    private final DeveloperConfigRepository developerConfigRepository;

    @Override
    public DeveloperConfigVO getConfig(String userId) {
        DeveloperConfig config = developerConfigRepository.findByUserId(userId).orElse(null);
        DeveloperConfigVO vo = new DeveloperConfigVO();
        if (config != null) {
            vo.setApiKey(config.getApiKey());
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeveloperConfigVO rotateApiKey(String userId) {
        DeveloperConfig config = developerConfigRepository.findByUserId(userId).orElseGet(() -> {
            DeveloperConfig newConfig = new DeveloperConfig();
            newConfig.setUserId(userId);
            return newConfig;
        });

        // 简单的 sk-xxx 生成方式，或者可以只用 UUID 替换 -
        String newApiKey = "sk-ys-" + UUID.randomUUID().toString().replace("-", "");
        config.setApiKey(newApiKey);

        developerConfigRepository.save(config);

        DeveloperConfigVO vo = new DeveloperConfigVO();
        vo.setApiKey(newApiKey);
        return vo;
    }
}
