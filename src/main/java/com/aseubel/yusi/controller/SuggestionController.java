package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import com.aseubel.yusi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final UserService userService;

    @PostMapping
    @RateLimiter(key = "suggestion-create", time = 60, count = 5, limitType = LimitType.IP)
    public Response<Suggestion> createSuggestion(@RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        String contactEmail = payload.get("contactEmail");

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容不能为空");
        }

        if (content.length() > 2000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容不能超过2000字");
        }

        Suggestion suggestion = suggestionService.createSuggestion(content.trim(), contactEmail);
        return Response.success(suggestion);
    }

    @GetMapping("/{suggestionId}")
    @Auth
    public Response<Suggestion> getSuggestion(@PathVariable String suggestionId) {
        if (!userService.checkAdmin(UserContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可查看建议详情");
        }
        return Response.success(suggestionService.getSuggestion(suggestionId));
    }
}
