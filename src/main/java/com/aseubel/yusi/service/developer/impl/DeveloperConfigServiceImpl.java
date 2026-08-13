package com.aseubel.yusi.service.developer.impl;

import com.aseubel.yusi.common.constant.DeveloperScope;
import com.aseubel.yusi.pojo.dto.developer.DeveloperConfigVO;
import com.aseubel.yusi.pojo.entity.DeveloperConfig;
import com.aseubel.yusi.repository.DeveloperConfigRepository;
import com.aseubel.yusi.service.developer.DeveloperConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeveloperConfigServiceImpl implements DeveloperConfigService {

    private static final Set<String> ALLOWED_SCOPES = Arrays.stream(DeveloperScope.values())
            .map(DeveloperScope::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final DeveloperConfigRepository developerConfigRepository;

    @Override
    public DeveloperConfigVO getConfig(String userId) {
        DeveloperConfig config = developerConfigRepository.findByUserId(userId).orElse(null);
        DeveloperConfigVO vo = new DeveloperConfigVO();
        if (config != null) {
            vo.setApiKey(config.getApiKey());
            vo.setScopes(parseScopes(config.getScopes()));
            vo.setActive(config.getRevokedAt() == null && config.getApiKey() != null);
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
        if (config.getScopes() == null || config.getScopes().isBlank()) {
            config.setScopes(DeveloperScope.MEMORY_READ.code());
        }
        config.setRevokedAt(null);

        developerConfigRepository.save(config);

        DeveloperConfigVO vo = new DeveloperConfigVO();
        vo.setApiKey(newApiKey);
        vo.setScopes(parseScopes(config.getScopes()));
        vo.setActive(true);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeveloperConfigVO updateScopes(String userId, List<String> scopes) {
        DeveloperConfig config = developerConfigRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("尚未生成 API Key"));
        List<String> normalized = normalizeScopes(scopes);
        config.setScopes(String.join(",", normalized));
        developerConfigRepository.save(config);
        return toVo(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeApiKey(String userId) {
        developerConfigRepository.findByUserId(userId).ifPresent(config -> {
            config.setRevokedAt(LocalDateTime.now());
            developerConfigRepository.save(config);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public String authorize(String apiKey, String requiredScope) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        DeveloperConfig config = developerConfigRepository.findByApiKey(apiKey).orElse(null);
        if (config == null || config.getRevokedAt() != null
                || !parseScopes(config.getScopes()).contains(requiredScope)) {
            return null;
        }
        return config.getUserId();
    }

    private DeveloperConfigVO toVo(DeveloperConfig config) {
        DeveloperConfigVO vo = new DeveloperConfigVO();
        vo.setApiKey(config.getApiKey());
        vo.setScopes(parseScopes(config.getScopes()));
        vo.setActive(config.getApiKey() != null && config.getRevokedAt() == null);
        return vo;
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of(DeveloperScope.MEMORY_READ.code());
        }
        List<String> normalized = scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .distinct()
                .toList();
        if (!ALLOWED_SCOPES.containsAll(normalized)) {
            throw new IllegalArgumentException("包含不支持的 API Key scope");
        }
        return normalized;
    }

    private List<String> parseScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of(DeveloperScope.MEMORY_READ.code());
        }
        return Arrays.stream(scopes.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .toList();
    }
}
