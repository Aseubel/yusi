package com.aseubel.yusi.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.pojo.dto.ai.DiaryChatRequest;
import com.aseubel.yusi.pojo.dto.diary.DiaryFootprint;
import com.aseubel.yusi.pojo.dto.diary.DiaryAttachmentBinding;
import com.aseubel.yusi.pojo.dto.diary.EditDiaryRequest;
import com.aseubel.yusi.pojo.dto.diary.WriteDiaryRequest;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.service.diary.DiaryService;
import jakarta.annotation.Resource;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.*;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:57
 */
@Auth
@RestController()
@RequestMapping("/api/diary")
public class DiaryController {

    @Resource
    private DiaryService diaryService;

    @GetMapping("/list")
    public Response<PagedModel<EntityModel<Diary>>> getDiaryList(
            @RequestParam String userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "true") boolean asc,
            PagedResourcesAssembler<Diary> assembler) {
        String currentUserId = UserContext.getUserId();
        if (!currentUserId.equals(userId)) {
            throw new com.aseubel.yusi.common.exception.AuthorizationException(
                    com.aseubel.yusi.common.exception.ErrorCode.FORBIDDEN, "无权访问其他用户的日记");
        }
        Page<Diary> diaryPage = diaryService.getDiaryList(currentUserId, pageNum, pageSize, sortBy, asc);
        if (diaryPage.hasContent()) {
            diaryPage.getContent().forEach(diary -> enrichDiaryAssets(diary, currentUserId));
        }
        return Response.success(assembler.toModel(diaryPage));
    }

    @PostMapping
    public Response<?> writeDiary(@RequestBody WriteDiaryRequest request) {
        Diary diary = request.toDiary();
        diary.setUserId(UserContext.getUserId());
        diaryService.addDiary(diary);
        return Response.success();
    }

    @PutMapping
    public Response<?> editDiary(@RequestBody EditDiaryRequest request) {
        Diary diary = request.toDiary();
        diary.setUserId(UserContext.getUserId());
        diaryService.editDiary(diary);
        return Response.success();
    }

    @GetMapping("/{diaryId}")
    public Response<Diary> getDiary(@PathVariable("diaryId") String diaryId) {
        String currentUserId = UserContext.getUserId();
        Diary diary = diaryService.getDiary(diaryId, currentUserId);
        if (diary != null) {
            enrichDiaryAssets(diary, currentUserId);
        }
        return Response.success(diary);
    }

    private void enrichDiaryAssets(Diary diary, String userId) {
        if (StrUtil.isNotBlank(diary.getImages())) {
            diary.setImageObjectKeys(JSONUtil.toList(diary.getImages(), String.class));
            diary.setImages(diaryService.convertImagesToUrls(diary.getImages(), userId));
        } else {
            diary.setImageObjectKeys(List.of());
            diary.setImages(JSONUtil.toJsonStr(List.of()));
        }
        List<DiaryAttachmentBinding> bindings = diaryService.convertAttachmentBindingsToUrls(
                diary.getAttachmentBindingsJson(), userId);
        diary.setAttachmentBindings(bindings);
        if (StrUtil.isBlank(diary.getAttachmentDisplayMode())) {
            diary.setAttachmentDisplayMode("INLINE");
        }
    }

    @PostMapping("/chat")
    public Response<String> chat(@RequestBody DiaryChatRequest request) {
        // This endpoint is deprecated in favor of /api/ai/chat/stream
        return Response.fail("Please use /api/ai/chat/stream for chat interaction");
    }

    /**
     * 获取用户足迹列表（有地理位置的日记）
     */
    @GetMapping("/footprints")
    public Response<List<DiaryFootprint>> getFootprints(
            @RequestParam String userId) {
        if (!UserContext.getUserId().equals(userId)) {
            throw new com.aseubel.yusi.common.exception.AuthorizationException(
                    com.aseubel.yusi.common.exception.ErrorCode.FORBIDDEN, "无权访问其他用户的足迹");
        }
        List<Diary> diaries = diaryService.getFootprints(UserContext.getUserId());
        List<DiaryFootprint> footprints = diaries.stream()
                .map(d -> new DiaryFootprint(
                        d.getDiaryId(),
                        d.getLatitude(),
                        d.getLongitude(),
                        d.getPlaceName(),
                        d.getAddress(),
                        d.getCreateTime(),
                        d.getEmotion()))
                .toList();
        return Response.success(footprints);
    }
}
