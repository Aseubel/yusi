package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.disruptor.DisruptorProducer;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.pojo.dto.ai.DiaryChatRequest;
import com.aseubel.yusi.pojo.dto.diary.DiaryFootprint;
import com.aseubel.yusi.pojo.dto.diary.EditDiaryRequest;
import com.aseubel.yusi.pojo.dto.diary.WriteDiaryRequest;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.service.diary.DiaryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private DiaryService diaryService;

    @Resource
    private DisruptorProducer disruptorProducer;

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
        return Response.success(assembler.toModel(diaryPage));
    }

    @PostMapping
    public Response<?> writeDiary(@RequestBody WriteDiaryRequest request) {
        Diary diary = diaryService.addDiary(request.toDiary());
        disruptorProducer.publish(diary, EventType.DIARY_WRITE);
        return Response.success();
    }

    @PutMapping
    public Response<?> editDiary(@RequestBody EditDiaryRequest request) {
        Diary diary = diaryService.editDiary(request.toDiary());
        disruptorProducer.publish(diary, EventType.DIARY_MODIFY);
        return Response.success();
    }

    @GetMapping("/{diaryId}")
    public Response<Diary> getDiary(@PathVariable("diaryId") String diaryId) {
        Diary diary = diaryService.getDiary(diaryId);
        disruptorProducer.publish(diary, EventType.DIARY_READ);
        return Response.success(diary);
    }

    @PostMapping("/chat")
    public Response<String> chat(@RequestBody DiaryChatRequest request) {
        // This endpoint is deprecated in favor of /api/ai/chat/stream
        return Response.fail("Please use /api/ai/chat/stream for chat interaction");
    }

    @PostMapping("/generate-response/{diaryId}")
    public Response<?> generateResponse(@PathVariable("diaryId") String diaryId) {
        diaryService.generateAiResponse(diaryId);
        return Response.success();
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
