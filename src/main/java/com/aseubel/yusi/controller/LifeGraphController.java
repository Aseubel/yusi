package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.service.lifegraph.CommunityInsightService;
import com.aseubel.yusi.service.lifegraph.EmotionTimelineService;
import com.aseubel.yusi.service.lifegraph.LifeGraphMergeSuggestionService;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
import com.aseubel.yusi.service.lifegraph.LifeTimelineService;
import com.aseubel.yusi.service.lifegraph.dto.CommunityInsight;
import com.aseubel.yusi.service.lifegraph.dto.EmotionTimeline;
import com.aseubel.yusi.service.lifegraph.dto.LifeChapter;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphMergeSuggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Auth
@RestController
@RequestMapping("/api/lifegraph")
@RequiredArgsConstructor
public class LifeGraphController {

    private final LifeGraphQueryService queryService;
    private final LifeTimelineService timelineService;
    private final CommunityInsightService communityInsightService;
    private final EmotionTimelineService emotionTimelineService;
    private final LifeGraphMergeSuggestionService mergeSuggestionService;

    @GetMapping("/search")
    public Response<String> search(@RequestParam String query) {
        String userId = UserContext.getUserId();
        return Response.success(queryService.localSearch(userId, query, 5, 50, 5));
    }

    @GetMapping("/timeline")
    public Response<List<LifeChapter>> getTimeline() {
        String userId = UserContext.getUserId();
        return Response.success(timelineService.getLifeChapters(userId));
    }

    @GetMapping("/communities")
    public Response<List<CommunityInsight>> getCommunities() {
        String userId = UserContext.getUserId();
        return Response.success(communityInsightService.detectCommunities(userId));
    }

    @GetMapping("/communities/{communityId}")
    public Response<CommunityInsight> getCommunityDetail(@PathVariable String communityId) {
        String userId = UserContext.getUserId();
        return Response.success(communityInsightService.getCommunityDetail(userId, communityId));
    }

    @GetMapping("/emotions")
    public Response<EmotionTimeline> getEmotionTimeline(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        String userId = UserContext.getUserId();
        return Response.success(emotionTimelineService.getEmotionTimeline(userId, startDate, endDate));
    }

    @GetMapping("/emotions/triggers")
    public Response<List<EmotionTimeline.EmotionTrigger>> getEmotionTriggers(
            @RequestParam(defaultValue = "10") int limit) {
        String userId = UserContext.getUserId();
        return Response.success(emotionTimelineService.getEmotionTriggers(userId, limit));
    }

    /**
     * 获取待处理的合并建议
     */
    @GetMapping("/merge-suggestions")
    public Response<List<LifeGraphMergeSuggestion>> getMergeSuggestions(
            @RequestParam(defaultValue = "10") int limit) {
        String userId = UserContext.getUserId();
        return Response.success(mergeSuggestionService.getPendingSuggestions(userId, limit));
    }

    /**
     * 接受合并建议
     */
    @PostMapping("/merge-suggestions/{judgmentId}/accept")
    public Response<Void> acceptMerge(@PathVariable Long judgmentId) {
        mergeSuggestionService.acceptMerge(judgmentId);
        return Response.success(null);
    }

    /**
     * 拒绝合并建议
     */
    @PostMapping("/merge-suggestions/{judgmentId}/reject")
    public Response<Void> rejectMerge(@PathVariable Long judgmentId) {
        mergeSuggestionService.rejectMerge(judgmentId);
        return Response.success(null);
    }
}
