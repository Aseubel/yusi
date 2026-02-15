package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import com.aseubel.yusi.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suggestions")
@CrossOrigin("*")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final UserService userService;

    @PostMapping
    @Auth
    public Response<Suggestion> createSuggestion(@RequestBody Map<String, String> payload) {
        String userId = UserContext.getUserId();
        String content = payload.get("content");
        String contactEmail = payload.get("contactEmail");

        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容不能为空");
        }

        if (content.length() > 2000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "内容不能超过2000字");
        }

        Suggestion suggestion = suggestionService.createSuggestion(userId, content.trim(), contactEmail);
        return Response.success(suggestion);
    }

    @GetMapping
    @Auth
    public Response<Page<Suggestion>> getUserSuggestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = UserContext.getUserId();
        return Response.success(suggestionService.getUserSuggestions(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/{suggestionId}")
    @Auth
    public Response<Suggestion> getSuggestion(@PathVariable String suggestionId) {
        return Response.success(suggestionService.getSuggestion(suggestionId));
    }
}
