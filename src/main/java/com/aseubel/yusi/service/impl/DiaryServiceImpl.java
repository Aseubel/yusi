package com.aseubel.yusi.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.DiaryService;
import com.aseubel.yusi.service.ai.Assistant;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * @author Aseubel
 * @date 2025/5/7 上午9:57
 */
@Service
public class DiaryServiceImpl implements DiaryService {

    @Autowired
    private DiaryRepository diaryRepository;

    @Resource
    private Assistant diaryRAGAssistant;

    @Override
    public Diary addDiary(Diary diary) {
        diary.generateId();
        return diaryRepository.save(diary);
    }

    @Override
    public Diary editDiary(Diary diary) {
        Diary existingDiary = diaryRepository.findByDiaryId(diary.getDiaryId());
        if (ObjectUtil.isNotEmpty(existingDiary)) {
            diary.setId(existingDiary.getId());
            return diaryRepository.save(diary);
        }
        return null;
    }

    @Override
    public Diary getDiary(String diaryId) {
        return diaryRepository.findByDiaryId(diaryId);
    }

    @Override
    public String chatWithDiaryRAG(String userId, String query) {
        return diaryRAGAssistant.chat(userId, query);
    }

    @Override
    public Page<Diary> getDiaryList(String userId, int pageNum, int pageSize, String sortBy, boolean asc) {
        // 处理默认排序字段
        String actualSort = StrUtil.isBlank(sortBy) ? "createTime" : sortBy;

        // 构建分页请求（注意Spring Data页码从0开始）
        Sort sort = Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, actualSort);
        PageRequest pageRequest = PageRequest.of(pageNum - 1, pageSize, sort);

        Example<Diary> example = Example.of(Diary.builder().userId(userId).build());
        return diaryRepository.findAll(example, pageRequest);

        // 如需带条件查询（示例）
        // return diaryRepository.findByUserId("当前用户ID", pageRequest);
    }
}
