package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.event.DiaryChangedEvent;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.service.plaza.EmotionAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 异步情感分析服务
 * 
 * 将情感分析从日记保存的同步流程中剥离，避免阻塞用户请求
 * 
 * @author Aseubel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisService {

    private final EmotionAnalyzer emotionAnalyzer;
    private final DiaryRepository diaryRepository;

    private static final Set<String> VALID_EMOTIONS = Set.of(
            "Joy", "Sadness", "Anxiety", "Love", "Anger",
            "Fear", "Hope", "Calm", "Confusion", "Neutral");

    /**
     * 异步监听日记变更事件，进行情感分析
     */
    @Async
    @EventListener
    public void onDiaryChanged(DiaryChangedEvent event) {
        Diary diary = event.getDiary();
        
        if (event.getType() != DiaryChangedEvent.Type.WRITE 
                && event.getType() != DiaryChangedEvent.Type.MODIFY) {
            return;
        }

        String content = diary.getPlainContent();
        if (content == null || content.trim().isEmpty()) {
            log.debug("日记 {} 内容为空，跳过情感分析", diary.getDiaryId());
            return;
        }

        try {
            String emotion = analyzeEmotion(content);
            diaryRepository.updateEmotion(diary.getDiaryId(), emotion);
            log.info("日记 {} 情感分析完成: {}", diary.getDiaryId(), emotion);
        } catch (Exception e) {
            log.error("日记 {} 情感分析失败: {}", diary.getDiaryId(), e.getMessage());
        }
    }

    /**
     * 分析文本情感
     */
    private String analyzeEmotion(String content) {
        try {
            String result = emotionAnalyzer.analyzeEmotion(content);
            String cleaned = result == null ? "" : result.trim().replaceAll("[\\n\\r]", "");
            
            if (VALID_EMOTIONS.contains(cleaned)) {
                return cleaned;
            }
            
            for (String valid : VALID_EMOTIONS) {
                if (cleaned.toLowerCase().contains(valid.toLowerCase())) {
                    return valid;
                }
            }
            
            return "Neutral";
        } catch (Exception e) {
            log.warn("情感分析异常，使用默认值: {}", e.getMessage());
            return "Neutral";
        }
    }
}
