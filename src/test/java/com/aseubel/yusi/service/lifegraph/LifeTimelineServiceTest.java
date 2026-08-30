package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.service.lifegraph.dto.LifeChapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LifeTimelineServiceTest {

    @Mock
    private LifeGraphEntityRepository entityRepository;

    @Test
    void timelinePrefersEntityImportanceOverLegacyPropsImportance() {
        LifeGraphEntity event = LifeGraphEntity.builder()
                .id(7L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Event)
                .nameNorm("first-trip")
                .displayName("第一次旅行")
                .firstMentionDate(LocalDate.of(2026, 8, 1))
                .mentionCount(1)
                .relationCount(0)
                .importance(0.9)
                .props("{\"importance\":0.1,\"emotion\":\"Joy\"}")
                .hidden(false)
                .build();
        when(entityRepository.findAllVisibleByUserIdAndType(
                eq("user-1"), eq(LifeGraphEntity.EntityType.Event)))
                .thenReturn(List.of(event));

        List<LifeChapter> chapters = new LifeTimelineService(entityRepository, new ObjectMapper())
                .getLifeChapters("user-1");

        assertEquals(0.9, chapters.get(0).getNodes().get(0).getImportance());
        assertEquals("Joy", chapters.get(0).getNodes().get(0).getEmotion());
    }

    @Test
    void timelineSkipsEventsWithoutAConcreteDate() {
        LifeGraphEntity event = LifeGraphEntity.builder()
                .id(8L)
                .userId("user-1")
                .type(LifeGraphEntity.EntityType.Event)
                .displayName("没有日期的事件")
                .mentionCount(1)
                .relationCount(0)
                .importance(0.9)
                .hidden(false)
                .build();
        when(entityRepository.findAllVisibleByUserIdAndType(
                eq("user-1"), eq(LifeGraphEntity.EntityType.Event)))
                .thenReturn(List.of(event));

        assertEquals(List.of(), new LifeTimelineService(entityRepository, new ObjectMapper())
                .getLifeChapters("user-1"));
    }
}
