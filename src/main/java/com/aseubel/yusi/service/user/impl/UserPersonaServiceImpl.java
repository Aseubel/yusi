package com.aseubel.yusi.service.user.impl;

import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.user.UserPersonaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPersonaServiceImpl implements UserPersonaService {

    private final UserPersonaRepository userPersonaRepository;

    @Override
    public UserPersona getUserPersona(String userId) {
        return userPersonaRepository.findVisibleByUserId(userId, LocalDateTime.now())
                .orElse(UserPersona.builder().userId(userId).build());
    }

    @Override
    public UserPersona getMatchableUserPersona(String userId) {
        return userPersonaRepository.findMatchableByUserId(userId, LocalDateTime.now())
                .orElse(UserPersona.builder().userId(userId).build());
    }

    @Override
    @Transactional
    public UserPersona updateUserPersona(String userId, UserPersona persona) {
        UserPersona existing = userPersonaRepository.findByUserId(userId)
                .orElse(UserPersona.builder().userId(userId).build());

        if (persona != null) {
            if (persona.getPreferredName() != null) {
                existing.setPreferredName(persona.getPreferredName());
            }
            if (persona.getLocation() != null) {
                existing.setLocation(persona.getLocation());
            }
            if (persona.getInterests() != null) {
                existing.setInterests(persona.getInterests());
            }
            if (persona.getTone() != null) {
                existing.setTone(persona.getTone());
            }
            if (persona.getCustomInstructions() != null) {
                existing.setCustomInstructions(persona.getCustomInstructions());
            }
            if (persona.getSourceType() != null && !"UNKNOWN".equalsIgnoreCase(persona.getSourceType())) {
                existing.setSourceType(persona.getSourceType());
                existing.setSourceId(persona.getSourceId());
                if (persona.getConfidence() != null) {
                    existing.setConfidence(persona.getConfidence());
                }
            }
        }

        existing.setUserId(userId);
        existing.setUpdatedAt(LocalDateTime.now());
        return userPersonaRepository.save(existing);
    }
}
