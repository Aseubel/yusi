package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.prompt.PromptResponse;
import com.aseubel.yusi.pojo.dto.prompt.PromptSaveRequest;
import com.aseubel.yusi.pojo.dto.prompt.PromptUpdateRequest;
import com.aseubel.yusi.pojo.entity.PromptTemplate;
import com.aseubel.yusi.service.ai.PromptService;
import com.aseubel.yusi.service.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/prompt")
@Auth // Protect all endpoints
@CrossOrigin("*")
@Validated
public class PromptController {

    @Autowired
    private PromptService promptService;

    @Autowired
    private UserService userService;

    private void checkAdmin() {
        String userId = UserContext.getUserId();
        if (!userService.checkAdmin(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Permission denied: Admin access required");
        }
    }

    @GetMapping("/{name}")
    public Response<String> getPrompt(
            @PathVariable @Size(max = 255) String name,
            @RequestParam(defaultValue = "zh-CN") @Size(max = 16) String locale) {
        return Response.success(promptService.getPrompt(name, normalize(locale)));
    }

    @GetMapping("/search")
    public Response<Page<PromptResponse>> searchPrompts(
            @RequestParam(required = false) @Size(max = 255) String name,
            @RequestParam(required = false) @Size(max = 64) String scope,
            @RequestParam(required = false) @Size(max = 16) String locale,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        checkAdmin();
        Page<PromptTemplate> result = promptService.searchPrompts(
                normalize(name),
                normalize(scope),
                normalize(locale),
                active,
                page,
                size);
        return Response.success(result.map(PromptResponse::from));
    }

    @PostMapping("/save")
    public Response<PromptResponse> savePrompt(@Valid @RequestBody PromptSaveRequest request) {
        checkAdmin();
        String userId = UserContext.getUserId();
        PromptTemplate entity = toEntity(request);
        return Response.success(PromptResponse.from(promptService.savePrompt(entity, userId)));
    }

    @PutMapping("/{id}")
    public Response<PromptResponse> updatePrompt(@PathVariable Long id,
            @Valid @RequestBody PromptUpdateRequest request) {
        checkAdmin();
        if (isUpdateEmpty(request)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "至少提供一个需要更新的字段");
        }
        String userId = UserContext.getUserId();
        PromptTemplate entity = toEntity(request);
        return Response.success(PromptResponse.from(promptService.updatePrompt(id, entity, userId)));
    }

    @PostMapping("/{id}/activate")
    public Response<Void> activatePrompt(@PathVariable Long id) {
        checkAdmin();
        String userId = UserContext.getUserId();
        promptService.activatePrompt(id, userId);
        return Response.success();
    }

    @DeleteMapping("/{id}")
    public Response<Void> deletePrompt(@PathVariable Long id) {
        checkAdmin();
        promptService.deletePrompt(id);
        return Response.success();
    }

    private PromptTemplate toEntity(PromptSaveRequest request) {
        PromptTemplate entity = new PromptTemplate();
        entity.setName(normalize(request.getName()));
        entity.setTemplate(request.getTemplate());
        entity.setVersion(normalize(request.getVersion()));
        entity.setActive(request.getActive());
        entity.setScope(normalize(request.getScope()));
        entity.setLocale(normalize(request.getLocale()));
        entity.setDescription(request.getDescription());
        entity.setTags(request.getTags());
        entity.setIsDefault(request.getIsDefault());
        entity.setPriority(request.getPriority());
        return entity;
    }

    private PromptTemplate toEntity(PromptUpdateRequest request) {
        PromptTemplate entity = new PromptTemplate();
        entity.setName(normalize(request.getName()));
        entity.setTemplate(request.getTemplate());
        entity.setVersion(normalize(request.getVersion()));
        entity.setActive(request.getActive());
        entity.setScope(normalize(request.getScope()));
        entity.setLocale(normalize(request.getLocale()));
        entity.setDescription(request.getDescription());
        entity.setTags(request.getTags());
        entity.setIsDefault(request.getIsDefault());
        entity.setPriority(request.getPriority());
        return entity;
    }

    private boolean isUpdateEmpty(PromptUpdateRequest request) {
        return request.getName() == null
                && request.getTemplate() == null
                && request.getVersion() == null
                && request.getActive() == null
                && request.getScope() == null
                && request.getLocale() == null
                && request.getDescription() == null
                && request.getTags() == null
                && request.getIsDefault() == null
                && request.getPriority() == null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
