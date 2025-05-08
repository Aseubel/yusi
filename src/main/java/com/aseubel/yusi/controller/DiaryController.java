package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.pojo.dto.WriteDiaryRequest;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.service.DiaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:57
 */
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/diary")
public class DiaryController {

    @Autowired
    private DiaryService diaryService;

    @PostMapping
    public Response<?> writeDiary(@RequestBody WriteDiaryRequest request) {
        Diary diary = diaryService.addDiary(request.toDiary());
        return Response.success();
    }


}
