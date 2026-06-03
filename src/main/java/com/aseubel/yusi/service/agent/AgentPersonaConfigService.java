package com.aseubel.yusi.service.agent;

import com.aseubel.yusi.pojo.dto.agent.AgentPersonaConfigRequest;
import com.aseubel.yusi.pojo.entity.AgentPersonaConfig;
import com.aseubel.yusi.repository.AgentPersonaConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Agent 人格配置管理服务。
 *
 * @author Aseubel
 * @date 2026/06/02
 */
@Service
@RequiredArgsConstructor
public class AgentPersonaConfigService {

    private final AgentPersonaConfigRepository configRepository;

    @Transactional(readOnly = true)
    public AgentPersonaConfig getConfig(String userId) {
        return configRepository.findByUserId(userId)
                .orElseGet(() -> AgentPersonaConfig.builder().userId(userId).build());
    }

    @Transactional
    public AgentPersonaConfig updateConfig(String userId, AgentPersonaConfigRequest request) {
        AgentPersonaConfig config = configRepository.findByUserId(userId)
                .orElseGet(() -> AgentPersonaConfig.builder().userId(userId).build());

        if (request.getPersonalityStyle() != null) {
            config.setPersonalityStyle(request.getPersonalityStyle());
        }
        if (request.getProactiveFrequency() != null) {
            config.setProactiveFrequency(request.getProactiveFrequency());
        }
        if (request.getQuietHoursStart() != null) {
            config.setQuietHoursStart(request.getQuietHoursStart());
        }
        if (request.getQuietHoursEnd() != null) {
            config.setQuietHoursEnd(request.getQuietHoursEnd());
        }
        if (request.getAnniversaryReminderEnabled() != null) {
            config.setAnniversaryReminderEnabled(request.getAnniversaryReminderEnabled());
        }
        if (request.getWeeklyReportEnabled() != null) {
            config.setWeeklyReportEnabled(request.getWeeklyReportEnabled());
        }

        return configRepository.save(config);
    }
}
