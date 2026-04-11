package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.service.lifegraph.CommunityInsightService;
import com.aseubel.yusi.service.lifegraph.EmotionTimelineService;
import com.aseubel.yusi.service.lifegraph.LifeGraphDataService;
import com.aseubel.yusi.service.lifegraph.LifeGraphMergeSuggestionService;
import com.aseubel.yusi.service.lifegraph.LifeTimelineService;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
import com.aseubel.yusi.service.lifegraph.dto.CommunityInsight;
import com.aseubel.yusi.service.lifegraph.dto.EmotionTimeline;
import com.aseubel.yusi.service.lifegraph.dto.GraphSnapshotDTO;
import com.aseubel.yusi.service.lifegraph.dto.LifeChapter;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphMergeSuggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    private final LifeGraphDataService dataService;

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

    /**
     * @deprecated 此接口已废弃，调用AI分析消耗token过高三方原因
     * 获取情绪时间线数据
     */
    @Deprecated
    @GetMapping("/emotions")
    public Response<EmotionTimeline> getEmotionTimeline(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return Response.<EmotionTimeline>builder()
                .code(410)
                .info("此接口已废弃，会消耗大量AI token，请勿调用")
                .build();
    }

    /**
     * @deprecated 此接口已废弃
     */
    @Deprecated
    @GetMapping("/emotions/triggers")
    public Response<List<EmotionTimeline.EmotionTrigger>> getEmotionTriggers(
            @RequestParam(defaultValue = "10") int limit) {
        return Response.<List<EmotionTimeline.EmotionTrigger>>builder()
                .code(410)
                .info("此接口已废弃，会消耗大量AI token，请勿调用")
                .build();
    }

    @GetMapping("/merge-suggestions")
    public Response<List<LifeGraphMergeSuggestion>> getMergeSuggestions(
            @RequestParam(defaultValue = "10") int limit) {
        String userId = UserContext.getUserId();
        return Response.success(mergeSuggestionService.getPendingSuggestions(userId, limit));
    }

    @PostMapping("/merge-suggestions/{judgmentId}/accept")
    public Response<Void> acceptMerge(@PathVariable Long judgmentId) {
        mergeSuggestionService.acceptMerge(judgmentId);
        return Response.success(null);
    }

    @PostMapping("/merge-suggestions/{judgmentId}/reject")
    public Response<Void> rejectMerge(@PathVariable Long judgmentId) {
        mergeSuggestionService.rejectMerge(judgmentId);
        return Response.success(null);
    }

    // ======================== 3D 图谱数据 API ========================

    /**
     * 分页获取全图数据
     */
    @GetMapping("/graph")
    public Response<GraphSnapshotDTO> getFullGraph(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        String userId = UserContext.getUserId();
        return Response.success(dataService.getFullGraph(userId, page, Math.min(size, 500)));
    }

    /**
     * BFS 渐进加载 - 从中心节点向外扩展
     */
    @GetMapping("/graph/bfs")
    public Response<GraphSnapshotDTO> getGraphBfs(
            @RequestParam Long centerId,
            @RequestParam(defaultValue = "2") int depth,
            @RequestParam(defaultValue = "500") int maxNodes) {
        String userId = UserContext.getUserId();
        return Response.success(dataService.getGraphBfs(userId, centerId, Math.min(depth, 5), Math.min(maxNodes, 1000)));
    }

    /**
     * 创建节点
     */
    @PostMapping("/entities")
    public Response<LifeGraphEntity> createEntity(@RequestBody Map<String, String> body) {
        String userId = UserContext.getUserId();
        LifeGraphEntity entity = dataService.createEntity(userId,
                body.get("displayName"), body.get("type"),
                body.getOrDefault("summary", null),
                body.getOrDefault("props", null));
        return Response.success(entity);
    }

    /**
     * 更新节点（支持乐观锁版本校验）
     */
    @PutMapping("/entities/{id}")
    public Response<LifeGraphEntity> updateEntity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String userId = UserContext.getUserId();
        try {
            Long version = body.get("version") != null ? Long.valueOf(body.get("version").toString()) : null;
            LifeGraphEntity entity = dataService.updateEntity(userId, id,
                    (String) body.get("displayName"),
                    (String) body.get("type"),
                    (String) body.get("summary"),
                    (String) body.get("props"),
                    version);
            return Response.success(entity);
        } catch (ObjectOptimisticLockingFailureException e) {
            return Response.<LifeGraphEntity>builder().code(409).info("版本冲突，数据已被其他操作修改，请刷新后重试").build();
        }
    }

    /**
     * 删除节点
     */
    @DeleteMapping("/entities/{id}")
    public Response<Void> deleteEntity(@PathVariable Long id) {
        String userId = UserContext.getUserId();
        dataService.deleteEntity(userId, id);
        return Response.success(null);
    }

    /**
     * 创建关系
     */
    @PostMapping("/relations")
    public Response<LifeGraphRelation> createRelation(@RequestBody Map<String, Object> body) {
        String userId = UserContext.getUserId();
        BigDecimal confidence = body.get("confidence") != null ?
                new BigDecimal(body.get("confidence").toString()) : null;
        Integer weight = body.get("weight") != null ?
                Integer.valueOf(body.get("weight").toString()) : null;
        LifeGraphRelation relation = dataService.createRelation(userId,
                Long.valueOf(body.get("sourceId").toString()),
                Long.valueOf(body.get("targetId").toString()),
                (String) body.get("type"),
                confidence, weight);
        return Response.success(relation);
    }

    /**
     * 更新关系（支持乐观锁版本校验）
     */
    @PutMapping("/relations/{id}")
    public Response<LifeGraphRelation> updateRelation(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String userId = UserContext.getUserId();
        try {
            BigDecimal confidence = body.get("confidence") != null ?
                    new BigDecimal(body.get("confidence").toString()) : null;
            Integer weight = body.get("weight") != null ?
                    Integer.valueOf(body.get("weight").toString()) : null;
            Long version = body.get("version") != null ? Long.valueOf(body.get("version").toString()) : null;
            LifeGraphRelation relation = dataService.updateRelation(userId, id,
                    (String) body.get("type"), confidence, weight, version);
            return Response.success(relation);
        } catch (ObjectOptimisticLockingFailureException e) {
            return Response.<LifeGraphRelation>builder().code(409).info("版本冲突，数据已被其他操作修改，请刷新后重试").build();
        }
    }

    /**
     * 删除关系
     */
    @DeleteMapping("/relations/{id}")
    public Response<Void> deleteRelation(@PathVariable Long id) {
        String userId = UserContext.getUserId();
        dataService.deleteRelation(userId, id);
        return Response.success(null);
    }
}
