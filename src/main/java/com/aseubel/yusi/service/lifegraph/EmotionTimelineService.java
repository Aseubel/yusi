package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.service.lifegraph.dto.EmotionTimeline;

import java.time.LocalDate;
import java.util.List;

public interface EmotionTimelineService {

    EmotionTimeline getEmotionTimeline(String userId, LocalDate startDate, LocalDate endDate);

    List<EmotionTimeline.EmotionTrigger> getEmotionTriggers(String userId, int limit);
}
