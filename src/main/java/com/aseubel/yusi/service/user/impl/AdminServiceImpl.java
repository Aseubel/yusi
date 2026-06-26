package com.aseubel.yusi.service.user.impl;

import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.admin.AdminStatsResponse;
import com.aseubel.yusi.pojo.dto.admin.ScenarioAuditRequest;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.SituationRoomRepository;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.repository.SuggestionRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.user.AdminService;
import com.aseubel.yusi.service.user.TokenService;
import com.aseubel.yusi.redis.service.IRedisService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.DeleteReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final DiaryRepository diaryRepository;
    private final SituationRoomRepository situationRoomRepository;
    private final SituationScenarioRepository situationScenarioRepository;
    private final SuggestionRepository suggestionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final IRedisService redissonService;
    private final MilvusClientV2 milvusClientV2;

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
            // 先尝试按 username 搜索
            Page<User> users = userRepository.findByUserNameContaining(search, pageable);
            if (users.hasContent()) {
                return users;
            }
            // 如果没找到，尝试按 userId 搜索
            return userRepository.findByUserIdContaining(search, pageable);
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
    public Page<SituationScenario> getAllScenarios(Pageable pageable, Integer status) {
        if (status != null) {
            return situationScenarioRepository.findByStatus(status, pageable);
        }
        return situationScenarioRepository.findAll(pageable);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deregisterUser(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }

        String currentUserId = UserContext.getUserId();
        if (userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot deregister yourself");
        }

        User currentUser = userRepository.findByUserId(currentUserId);
        int currentAdminLevel = currentUser != null && currentUser.getPermissionLevel() != null ? currentUser.getPermissionLevel() : 0;
        int targetUserLevel = user.getPermissionLevel() != null ? user.getPermissionLevel() : 0;
        if (targetUserLevel >= currentAdminLevel) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot deregister users with equal or higher permission level");
        }

        log.info("Super admin {} is deregistering user {} and deleting all associated data", currentUserId, userId);

        // 1. Clean up active sessions and access tokens
        try {
            tokenService.deleteRefreshToken(userId);
            tokenService.removeAllDeviceTokens(userId);
        } catch (Exception e) {
            log.error("Failed to clean up tokens for user {}: {}", userId, e.getMessage());
        }

        // 2. Clean up LangChain memory cache in Redis
        try {
            redissonService.remove("yusi:langchain:" + userId);
        } catch (Exception e) {
            log.error("Failed to delete langchain cache for user {}: {}", userId, e.getMessage());
        }

        // 3. Clean up Milvus embeddings
        try {
            milvusClientV2.delete(DeleteReq.builder()
                    .collectionName("yusi_embedding_collection")
                    .filter("metadata[\"userId\"] == \"" + userId + "\"")
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete user {} embeddings from Milvus: {}", userId, e.getMessage());
        }

        // 4. Clean up Situation Rooms
        try {
            List<SituationRoom> rooms = situationRoomRepository.findByMembersContainingOrderByCreatedAtDesc(userId);
            for (SituationRoom room : rooms) {
                if (userId.equals(room.getOwnerId()) || room.getMembers() == null || room.getMembers().size() <= 2) {
                    // Delete room messages and room itself
                    jdbcTemplate.update("DELETE FROM room_message WHERE room_code = ?", room.getCode());
                    situationRoomRepository.delete(room);
                } else {
                    // Remove user from members list
                    room.getMembers().remove(userId);
                    if (room.getSubmissions() != null) {
                        room.getSubmissions().remove(userId);
                    }
                    if (room.getSubmissionVisibility() != null) {
                        room.getSubmissionVisibility().remove(userId);
                    }
                    if (room.getCancelVotes() != null) {
                        room.getCancelVotes().remove(userId);
                    }
                    situationRoomRepository.save(room);
                }
            }
        } catch (Exception e) {
            log.error("Failed to clean up situation rooms for user {}: {}", userId, e.getMessage());
        }

        // 5. Clean up other database tables using native queries
        String[] deleteQueries = {
            "DELETE FROM user_location WHERE user_id = ?",
            "DELETE FROM user_notification WHERE user_id = ?",
            "DELETE FROM user_persona WHERE user_id = ?",
            "DELETE FROM agent_persona_config WHERE user_id = ?",
            "DELETE FROM chat_memory_message WHERE memory_id = ?",
            "DELETE FROM cognitive_conflict WHERE user_id = ?",
            "DELETE FROM developer_config WHERE user_id = ?",
            "DELETE FROM diary WHERE user_id = ?",
            "DELETE FROM embedding_task WHERE user_id = ?",
            "DELETE FROM image_file WHERE user_id = ?",
            "DELETE FROM interface_daily_usage WHERE user_id = ?",
            "DELETE FROM life_graph_entity WHERE user_id = ?",
            "DELETE FROM life_graph_entity_alias WHERE user_id = ?",
            "DELETE FROM life_graph_mention WHERE user_id = ?",
            "DELETE FROM life_graph_merge_judgment WHERE user_id = ?",
            "DELETE FROM life_graph_relation WHERE user_id = ?",
            "DELETE FROM life_graph_task WHERE user_id = ?",
            "DELETE FROM match_feedback WHERE user_id = ?",
            "DELETE FROM match_profile WHERE user_id = ?",
            "DELETE FROM mid_term_memory WHERE user_id = ?",
            "DELETE FROM resonance_signal WHERE from_user_id = ? OR to_user_id = ?",
            "DELETE FROM room_message WHERE sender_id = ?",
            "DELETE FROM soul_card WHERE user_id = ?",
            "DELETE FROM soul_match WHERE user_a_id = ? OR user_b_id = ?",
            "DELETE FROM soul_message WHERE sender_id = ? OR receiver_id = ?",
            "DELETE FROM soul_report WHERE user_id = ?",
            "DELETE FROM soul_resonance WHERE user_id = ?",
            "DELETE FROM situation_scenario WHERE submitter_id = ?",
            "DELETE FROM user WHERE user_id = ?"
        };

        for (String query : deleteQueries) {
            try {
                if (query.contains("OR")) {
                    jdbcTemplate.update(query, userId, userId);
                } else {
                    jdbcTemplate.update(query, userId);
                }
            } catch (Exception e) {
                log.error("Failed to execute delete query [{}] for user {}: {}", query, userId, e.getMessage());
            }
        }
        log.info("Successfully deregistered user {} and cleaned up all associated data", userId);
    }
}
