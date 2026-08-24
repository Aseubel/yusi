package com.aseubel.yusi.service.web;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.web.IpRuleMatcher;
import com.aseubel.yusi.common.web.RuntimeAccessPolicySnapshot;
import com.aseubel.yusi.pojo.dto.admin.WebAccessPolicyResponse;
import com.aseubel.yusi.pojo.dto.admin.WebAccessPolicyUpdateRequest;
import com.aseubel.yusi.pojo.entity.WebAccessPolicy;
import com.aseubel.yusi.repository.WebAccessPolicyRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Stores a persisted policy while serving an immutable in-memory snapshot to filters. */
@Slf4j
@Service
public class RuntimeAccessPolicyService {

    private static final int MAX_RULES = 100;
    private static final int MAX_RULE_LENGTH = 128;
    private static final long DEFAULT_DEVELOPMENT_MODE_HOURS = 24;
    private static final long MAX_DEVELOPMENT_MODE_DAYS = 7;

    private final WebAccessPolicyRepository repository;
    private final Clock clock;
    private final List<String> bootstrapOrigins;
    private final boolean bootstrapDevelopmentMode;
    private final AtomicReference<RuntimeAccessPolicySnapshot> current;

    @Autowired
    public RuntimeAccessPolicyService(WebAccessPolicyRepository repository,
            @Value("${yusi.web.allowed-origin:http://localhost:5173}") String configuredOrigins,
            @Value("${yusi.web.dev-mode-enabled:false}") boolean configuredDevelopmentMode) {
        this(repository, configuredOrigins, configuredDevelopmentMode, Clock.systemDefaultZone());
    }

    RuntimeAccessPolicyService(WebAccessPolicyRepository repository, String configuredOrigins,
            boolean configuredDevelopmentMode, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.bootstrapOrigins = normalizeOrigins(Arrays.asList(StringUtils.commaDelimitedListToStringArray(
                configuredOrigins == null ? "" : configuredOrigins)), "bootstrap origins");
        this.bootstrapDevelopmentMode = configuredDevelopmentMode;
        this.current = new AtomicReference<>(bootstrapSnapshot());
    }

    @PostConstruct
    public void initialize() {
        refreshFromDatabase();
    }

    @Scheduled(fixedDelayString = "${yusi.web.policy-refresh-ms:5000}")
    public void refreshFromDatabase() {
        try {
            repository.findById(WebAccessPolicy.SINGLETON_ID).ifPresent(entity -> current.set(toSnapshot(entity)));
        } catch (RuntimeException exception) {
            log.warn("runtime web access policy refresh failed; keeping last known snapshot");
        }
    }

    public RuntimeAccessPolicySnapshot getEffectivePolicy() {
        return current.get();
    }

    public WebAccessPolicyResponse getPolicy() {
        return getPolicy(null);
    }

    public WebAccessPolicyResponse getPolicy(String currentClientIp) {
        return toResponse(current.get(), LocalDateTime.now(clock), currentClientIp);
    }

    @Transactional
    public synchronized WebAccessPolicyResponse updatePolicy(WebAccessPolicyUpdateRequest request, String operatorId,
            String currentClientIp) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Access policy is required");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean developmentModeEnabled = Boolean.TRUE.equals(request.developmentModeEnabled());
        LocalDateTime developmentModeExpiresAt = normalizeDevelopmentExpiry(
                developmentModeEnabled, request.developmentModeExpiresAt(), now);
        List<String> allowedOrigins = normalizeOrigins(request.allowedOrigins(), "allowed origin");
        List<String> blockedOrigins = normalizeOrigins(request.blockedOrigins(), "blocked origin");
        List<String> allowedIpRules = normalizeIpRules(request.allowedIpRules(), "allowed IP rule");
        List<String> blockedIpRules = normalizeIpRules(request.blockedIpRules(), "blocked IP rule");
        if (!isCurrentClientIpAllowed(currentClientIp, allowedIpRules, blockedIpRules)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "The current client IP must remain allowed to prevent administrator lockout");
        }

        WebAccessPolicy entity = repository.findById(WebAccessPolicy.SINGLETON_ID)
                .orElseGet(() -> {
                    WebAccessPolicy created = new WebAccessPolicy();
                    created.setId(WebAccessPolicy.SINGLETON_ID);
                    return created;
                });
        entity.setDevelopmentModeEnabled(developmentModeEnabled);
        entity.setDevelopmentModeExpiresAt(developmentModeExpiresAt);
        entity.setAllowedOrigins(new LinkedHashSet<>(allowedOrigins));
        entity.setBlockedOrigins(new LinkedHashSet<>(blockedOrigins));
        entity.setAllowedIpRules(new LinkedHashSet<>(allowedIpRules));
        entity.setBlockedIpRules(new LinkedHashSet<>(blockedIpRules));
        entity.setUpdatedBy(operatorId);

        WebAccessPolicy saved = repository.saveAndFlush(entity);
        current.set(toSnapshot(saved));
        return toResponse(current.get(), now, currentClientIp);
    }

    private RuntimeAccessPolicySnapshot bootstrapSnapshot() {
        LocalDateTime expiresAt = bootstrapDevelopmentMode
                ? LocalDateTime.now(clock).plusHours(DEFAULT_DEVELOPMENT_MODE_HOURS)
                : null;
        return new RuntimeAccessPolicySnapshot(
                bootstrapDevelopmentMode,
                expiresAt,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                bootstrapOrigins,
                null,
                null);
    }

    private RuntimeAccessPolicySnapshot toSnapshot(WebAccessPolicy entity) {
        return new RuntimeAccessPolicySnapshot(
                entity.isDevelopmentModeEnabled(),
                entity.getDevelopmentModeExpiresAt(),
                toList(entity.getAllowedOrigins()),
                toList(entity.getBlockedOrigins()),
                toList(entity.getAllowedIpRules()),
                toList(entity.getBlockedIpRules()),
                bootstrapOrigins,
                entity.getVersion(),
                entity.getUpdatedAt());
    }

    private WebAccessPolicyResponse toResponse(RuntimeAccessPolicySnapshot policy, LocalDateTime now,
            String currentClientIp) {
        return new WebAccessPolicyResponse(
                policy.developmentModeEnabled(),
                policy.developmentModeActive(now),
                policy.developmentModeExpiresAt(),
                policy.allowedOrigins(),
                policy.blockedOrigins(),
                policy.allowedIps(),
                policy.blockedIps(),
                policy.environmentOrigins(),
                currentClientIp,
                policy.version(),
                policy.updatedAt());
    }

    private LocalDateTime normalizeDevelopmentExpiry(boolean enabled, LocalDateTime requested, LocalDateTime now) {
        if (!enabled) {
            return null;
        }
        LocalDateTime expiry = requested == null ? now.plusHours(DEFAULT_DEVELOPMENT_MODE_HOURS) : requested;
        if (!expiry.isAfter(now)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Development mode expiry must be in the future");
        }
        if (expiry.isAfter(now.plusDays(MAX_DEVELOPMENT_MODE_DAYS))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Development mode expiry cannot exceed 7 days");
        }
        return expiry;
    }

    private List<String> normalizeOrigins(List<String> values, String label) {
        List<String> normalized = normalizeList(values, label);
        for (String origin : normalized) {
            validateOrigin(origin, label);
        }
        return normalized;
    }

    private List<String> normalizeIpRules(List<String> values, String label) {
        List<String> normalized = normalizeList(values, label);
        for (String rule : normalized) {
            if (rule.length() > MAX_RULE_LENGTH || !IpRuleMatcher.isValidRule(rule)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, label + " is invalid");
            }
        }
        return normalized;
    }

    private List<String> normalizeList(List<String> values, String label) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_RULES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + " count cannot exceed " + MAX_RULES);
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.length() > MAX_RULE_LENGTH) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, label + " is too long");
            }
            unique.add(normalized);
        }
        return List.copyOf(unique);
    }

    private void validateOrigin(String origin, String label) {
        if (origin.equals("*")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + " cannot be *");
        }
        String candidate = origin;
        if (origin.contains("*")) {
            if (!origin.matches("https?://(?:[A-Za-z0-9.-]+|\\[[0-9A-Fa-f:]+\\]):\\*")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, label + " wildcard must only target a port");
            }
            candidate = origin.replace(":*", ":65535");
        }
        try {
            URI uri = new URI(candidate);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getPath() != null && !uri.getPath().isEmpty()
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, label + " must be an HTTP(S) origin");
            }
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, label + " must be an HTTP(S) origin");
        }
    }

    private List<String> toList(Set<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private boolean isCurrentClientIpAllowed(String currentClientIp, List<String> allowedIpRules,
            List<String> blockedIpRules) {
        if (currentClientIp == null || currentClientIp.isBlank() || "unknown".equalsIgnoreCase(currentClientIp)) {
            return allowedIpRules.isEmpty() && blockedIpRules.isEmpty();
        }
        if (blockedIpRules.stream().anyMatch(rule -> IpRuleMatcher.matches(currentClientIp, rule))) {
            return false;
        }
        return allowedIpRules.isEmpty()
                || allowedIpRules.stream().anyMatch(rule -> IpRuleMatcher.matches(currentClientIp, rule));
    }
}
