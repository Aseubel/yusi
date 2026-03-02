package com.aseubel.yusi.service.user.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.aseubel.yusi.pojo.entity.UserPersona;
import com.aseubel.yusi.repository.UserPersonaRepository;
import com.aseubel.yusi.service.user.UserPersonaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPersonaServiceImpl implements UserPersonaService {

    private final UserPersonaRepository userPersonaRepository;

    @Override
    public UserPersona getUserPersona(String userId) {
        return userPersonaRepository.findByUserId(userId)
                .orElse(UserPersona.builder().userId(userId).build());
    }

    @Override
    @Transactional
    public UserPersona updateUserPersona(String userId, UserPersona persona) {
        UserPersona existing = userPersonaRepository.findByUserId(userId)
                .orElse(UserPersona.builder().userId(userId).build());

        // Copy non-null properties
        BeanUtil.copyProperties(persona, existing, CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true));
        
        // Ensure userId is set
        existing.setUserId(userId);
        
        return userPersonaRepository.save(existing);
    }
}
