package com.aseubel.yusi.controller;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.pojo.dto.ai.DiaryChatRequest;
import com.aseubel.yusi.pojo.dto.diary.DiaryFootprint;
import com.aseubel.yusi.pojo.dto.diary.EditDiaryRequest;
import com.aseubel.yusi.pojo.dto.diary.WriteDiaryRequest;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.service.diary.DiaryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@RestController()
@CrossOrigin("*")
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
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        Page<Diary> diaryPage = diaryService.getDiaryList(userId, pageNum, pageSize, sortBy, asc);
        if (diaryPage.hasContent()) {
            diaryPage.getContent().forEach(diary -> {
                if (StrUtil.isNotBlank(diary.getImages())) {
                    diary.setImages(diaryService.convertImagesToUrls(diary.getImages()));
                }
            });
        }
        return Response.success(assembler.toModel(diaryPage));
    }

    @PostMapping
    public Response<?> writeDiary(@RequestBody WriteDiaryRequest request) {
        diaryService.addDiary(request.toDiary());
        return Response.success();
    }

    @PutMapping
    public Response<?> editDiary(@RequestBody EditDiaryRequest request) {
        diaryService.editDiary(request.toDiary());
        return Response.success();
    }

    @GetMapping("/{diaryId}")
    public Response<Diary> getDiary(@PathVariable("diaryId") String diaryId) {
        Diary diary = diaryService.getDiary(diaryId);
        if (diary != null && StrUtil.isNotBlank(diary.getImages())) {
            diary.setImages(diaryService.convertImagesToUrls(diary.getImages()));
        }
        return Response.success(diary);
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
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        List<Diary> diaries = diaryService.getFootprints(userId);
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

