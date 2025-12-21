package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.user.UserService;
import lombok.Data;
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
@CrossOrigin("*")
public class MatchController {

    @Autowired
    private MatchService matchService;

    @Autowired
    private UserService userService;

    @Data
    public static class MatchSettingsRequest {
        private Boolean enabled;
        private String intent;
    }

    @PostMapping("/settings")
    public User updateSettings(@RequestBody MatchSettingsRequest request) {
        String userId = UserContext.getUserId();
        return userService.updateMatchSettings(userId, request.getEnabled(), request.getIntent());
    }

    @GetMapping("/recommendations")
    public List<SoulMatch> getRecommendations() {
        String userId = UserContext.getUserId();
        return matchService.getMatches(userId);
    }

    @Data
    public static class MatchActionRequest {
        private Integer action; // 1: Interested, 2: Skipped
    }

    @PostMapping("/{matchId}/action")
    public SoulMatch handleAction(@PathVariable Long matchId, @RequestBody MatchActionRequest request) {
        String userId = UserContext.getUserId();
        return matchService.handleMatchAction(userId, matchId, request.getAction());
    }

    // Dev endpoint to trigger matching manually
    @PostMapping("/run")
    public String runMatching() {
        matchService.runDailyMatching();
        return "Matching process triggered.";
    }
}
