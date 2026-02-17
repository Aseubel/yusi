package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.entity.Suggestion;
import com.aseubel.yusi.service.suggestion.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suggestions")
@CrossOrigin("*")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping
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
    public Response<Suggestion> getSuggestion(@PathVariable String suggestionId) {
        return Response.success(suggestionService.getSuggestion(suggestionId));
    }
}
