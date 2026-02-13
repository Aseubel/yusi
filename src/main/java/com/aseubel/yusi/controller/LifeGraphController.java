package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.service.lifegraph.LifeGraphQueryService;
import com.aseubel.yusi.service.lifegraph.LifeTimelineService;
import com.aseubel.yusi.service.lifegraph.dto.LifeChapter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Auth
@RestController
@RequestMapping("/api/lifegraph")
@RequiredArgsConstructor
public class LifeGraphController {

    private final LifeGraphQueryService queryService;
    private final LifeTimelineService timelineService;

    /**
     * 图谱本地搜索 (1-hop Context)
     * 返回结构化的图谱文本（YAML风格），用于调试或前端展示
     */
    @GetMapping("/search")
    public Response<String> search(@RequestParam String query) {
        String userId = UserContext.getUserId();
        // 默认限制：5个实体，每个实体50条关系，5条原文提及
        return Response.success(queryService.localSearch(userId, query, 5, 50, 5));
    }

    /**
     * 获取人生时间线章节
     */
    @GetMapping("/timeline")
    public Response<List<LifeChapter>> getTimeline() {
        String userId = UserContext.getUserId();
        return Response.success(timelineService.getLifeChapters(userId));
    }
}
