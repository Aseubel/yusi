package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.match.MatchActionRequest;
import com.aseubel.yusi.pojo.dto.match.ConnectionActionRequest;
import com.aseubel.yusi.pojo.dto.match.ConnectionFeedbackRequest;
import com.aseubel.yusi.pojo.dto.match.MatchRecommendationResponse;
import com.aseubel.yusi.pojo.dto.match.MatchSettingsRequest;
import com.aseubel.yusi.pojo.dto.match.MatchStatusResponse;
import com.aseubel.yusi.pojo.dto.user.UserResponse;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/12/21
 */
@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserService userService;

    @PostMapping("/settings")
    @RateLimiter(key = "match-settings", time = 60, count = 10, limitType = LimitType.USER)
    public Response<UserResponse> updateSettings(@RequestBody MatchSettingsRequest request) {
        String userId = UserContext.getUserId();
        return Response.success(UserResponse.from(
                userService.updateMatchSettings(userId, request.getEnabled(), request.getIntent())));
    }

    @GetMapping("/recommendations")
    public Response<List<MatchRecommendationResponse>> getRecommendations() {
        String userId = UserContext.getUserId();
        return Response.success(matchService.getMatches(userId));
    }

    @PostMapping("/{matchId}/action")
    @RateLimiter(key = "match-action", time = 60, count = 30, limitType = LimitType.USER)
    public Response<MatchRecommendationResponse> handleAction(@PathVariable Long matchId,
            @RequestBody MatchActionRequest request) {
        String userId = UserContext.getUserId();
        return Response.success(matchService.handleMatchAction(userId, matchId, request.getAction()));
    }

    @PostMapping("/{matchId}/feedback")
    @RateLimiter(key = "match-feedback", time = 60, count = 10, limitType = LimitType.USER)
    public Response<MatchRecommendationResponse> submitFeedback(@PathVariable Long matchId,
            @RequestBody ConnectionFeedbackRequest request) {
        return Response.success(matchService.submitConnectionFeedback(UserContext.getUserId(), matchId,
                request.getCategory()));
    }

    @PostMapping("/{matchId}/end")
    @RateLimiter(key = "match-end", time = 60, count = 10, limitType = LimitType.USER)
    public Response<MatchRecommendationResponse> endConnection(@PathVariable Long matchId,
            @RequestBody(required = false) ConnectionActionRequest request) {
        String reasonCategory = request != null ? request.getReasonCategory() : null;
        return Response.success(matchService.endConnection(UserContext.getUserId(), matchId, reasonCategory));
    }

    @PostMapping("/{matchId}/report")
    @RateLimiter(key = "match-report", time = 600, count = 3, limitType = LimitType.USER)
    public Response<MatchRecommendationResponse> reportConnection(@PathVariable Long matchId,
            @RequestBody(required = false) ConnectionActionRequest request) {
        String reasonCategory = request != null ? request.getReasonCategory() : null;
        return Response.success(matchService.reportConnection(UserContext.getUserId(), matchId, reasonCategory));
    }

    @PostMapping("/{matchId}/block")
    @RateLimiter(key = "match-block", time = 600, count = 3, limitType = LimitType.USER)
    public Response<MatchRecommendationResponse> blockConnection(@PathVariable Long matchId,
            @RequestBody(required = false) ConnectionActionRequest request) {
        String reasonCategory = request != null ? request.getReasonCategory() : null;
        return Response.success(matchService.blockConnection(UserContext.getUserId(), matchId, reasonCategory));
    }

    @GetMapping("/status")
    public Response<MatchStatusResponse> getStatus() {
        String userId = UserContext.getUserId();
        return Response.success(matchService.getMatchStatus(userId));
    }

    // Dev endpoint to trigger matching manually
    // @PostMapping("/run")
    public String runMatching() {
        matchService.runWeeklyMatching();
        return "Matching process triggered.";
    }
}
