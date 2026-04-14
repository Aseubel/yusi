package com.aseubel.yusi.service.lifegraph.impl;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.dto.cognition.CognitionIngestCommand;
import com.aseubel.yusi.pojo.dto.cognition.CognitionRoutingResult;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.service.lifegraph.LifeGraphCognitionBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class LifeGraphCognitionBridgeServiceImpl implements LifeGraphCognitionBridgeService {

    private final LifeGraphEntityRepository lifeGraphEntityRepository;

    @Override
    @Transactional
    public void bridge(CognitionIngestCommand command, CognitionRoutingResult routingResult) {
        if (command == null || StrUtil.isBlank(command.getUserId())) {
            return;
        }

        ensureUserEntity(command.getUserId());

        if (StrUtil.isNotBlank(command.getPlaceName())) {
            upsertEntity(command.getUserId(),
                    LifeGraphEntity.EntityType.Place,
                    normalize(command.getPlaceName()),
                    command.getPlaceName().trim(),
                    "用户长期记忆中出现的重要地点线索");
        }

        if ("EMOTION_PLAZA".equalsIgnoreCase(command.getSourceType()) && StrUtil.isNotBlank(command.getTopic())) {
            upsertEntity(command.getUserId(),
                    LifeGraphEntity.EntityType.Emotion,
                    normalize(command.getTopic()),
                    command.getTopic().trim(),
                    "用户在公开匿名表达中反复呈现的情绪主题");
        }

        if (routingResult != null && StrUtil.isNotBlank(routingResult.getInterests())) {
            Arrays.stream(routingResult.getInterests().split("[,，、/\\s]+"))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .limit(5)
                    .forEach(interest -> upsertEntity(command.getUserId(),
                            LifeGraphEntity.EntityType.Topic,
                            normalize(interest),
                            interest,
                            "用户相对稳定的兴趣或话题偏好"));
        }

        if ("DIARY".equalsIgnoreCase(command.getSourceType()) && StrUtil.isNotBlank(command.getTitle())) {
            upsertEntity(command.getUserId(),
                    LifeGraphEntity.EntityType.Topic,
                    normalize(command.getTitle()),
                    command.getTitle().trim(),
                    "用户在日记中反复形成的人生主题线索");
        }
    }

    private void ensureUserEntity(String userId) {
        lifeGraphEntityRepository.findByUserIdAndTypeAndNameNorm(userId, LifeGraphEntity.EntityType.User, "__user__")
                .orElseGet(() -> lifeGraphEntityRepository.save(LifeGraphEntity.builder()
                        .userId(userId)
                        .type(LifeGraphEntity.EntityType.User)
                        .nameNorm("__user__")
                        .displayName("我")
                        .summary("用户自身")
                        .mentionCount(1)
                        .relationCount(0)
                        .firstMentionDate(LocalDate.now())
                        .lastMentionAt(LocalDateTime.now())
                        .build()));
    }

    private void upsertEntity(String userId, LifeGraphEntity.EntityType type, String nameNorm,
            String displayName, String summary) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(nameNorm) || StrUtil.isBlank(displayName)) {
            return;
        }
        LifeGraphEntity entity = lifeGraphEntityRepository.findByUserIdAndTypeAndNameNorm(userId, type, nameNorm)
                .orElseGet(() -> LifeGraphEntity.builder()
                        .userId(userId)
                        .type(type)
                        .nameNorm(nameNorm)
                        .displayName(displayName)
                        .mentionCount(0)
                        .relationCount(0)
                        .firstMentionDate(LocalDate.now())
                        .build());
        entity.setDisplayName(displayName);
        entity.setSummary(summary);
        entity.setMentionCount((entity.getMentionCount() == null ? 0 : entity.getMentionCount()) + 1);
        entity.setLastMentionAt(LocalDateTime.now());
        if (entity.getFirstMentionDate() == null) {
            entity.setFirstMentionDate(LocalDate.now());
        }
        lifeGraphEntityRepository.save(entity);
    }

    private String normalize(String value) {
        return StrUtil.trim(value).toLowerCase();
    }
}
