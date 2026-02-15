package com.aseubel.yusi.service.user.impl;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.admin.AdminStatsResponse;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.user.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final SituationRoomRepository situationRoomRepository;
    private final SituationScenarioRepository situationScenarioRepository;
    private final SuggestionRepository suggestionRepository;

    @Override
    public AdminStatsResponse getStats() {
        return AdminStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalDiaries(diaryRepository.count())
                .totalRooms(situationRoomRepository.count())
                .pendingScenarios(situationScenarioRepository.findByStatus(0).size())
                .pendingSuggestions(suggestionRepository.countByStatus("PENDING"))
                .build();
    }

    @Override
    public Page<User> getUsers(Pageable pageable, String search) {
        if (search != null && !search.isEmpty()) {
            return userRepository.findByUserNameContaining(search, pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserPermission(String userId, Integer permissionLevel) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }
        user.setPermissionLevel(permissionLevel);
        userRepository.save(user);
    }

    @Override
    public void validatePermissionChange(String currentUserId, String targetUserId, Integer newLevel, Integer currentAdminLevel) {
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot modify your own permissions");
        }
        
        User targetUser = userRepository.findByUserId(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Target user not found");
        }
        
        int targetCurrentLevel = targetUser.getPermissionLevel() != null ? targetUser.getPermissionLevel() : 0;
        
        if (targetCurrentLevel >= currentAdminLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot modify users with equal or higher permission level");
        }
        
        if (newLevel >= currentAdminLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot set permission level equal or higher than your own");
        }
    }

    @Override
    public Page<SituationScenario> getPendingScenarios(Pageable pageable) {
        return situationScenarioRepository.findByStatus(0, pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditScenario(String scenarioId, ScenarioAuditRequest request) {
        SituationScenario scenario = situationScenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Scenario not found"));

        if (request.isApproved()) {
            scenario.setStatus(4);
        } else {
            scenario.setStatus(1);
            scenario.setRejectReason(request.getRejectReason());
        }
        situationScenarioRepository.save(scenario);
    }
}
