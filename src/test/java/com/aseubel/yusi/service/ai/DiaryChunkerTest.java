package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.Diary;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiaryChunkerTest {

    @Test
    void split_keepsShortParagraphsIntactAndAddsDiaryContext() {
        DocumentSplitter fallbackSplitter = mock(DocumentSplitter.class);
        TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
        when(tokenCountEstimator.estimateTokenCountInText(any())).thenReturn(10);
        DiaryChunker chunker = new DiaryChunker(fallbackSplitter, tokenCountEstimator);

        List<DiaryChunker.DiaryChunk> chunks = chunker.split(diary(), "第一段。\n\n第二段。");

        assertEquals(2, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
        assertEquals(2, chunks.get(0).count());
        assertEquals("d-1", chunks.get(0).diaryId());
        assertTrue(chunks.get(0).text().contains("日期：2026-07-13"));
        assertTrue(chunks.get(0).text().contains("标题：海边散步"));
        assertTrue(chunks.get(1).text().contains("地点：青岛"));
        assertTrue(chunks.get(1).text().contains("情绪：平静"));
        assertTrue(chunks.get(1).text().endsWith("第二段。"));
    }

    @Test
    void split_usesFallbackSplitterOnlyForOverlongParagraph() {
        DocumentSplitter fallbackSplitter = mock(DocumentSplitter.class);
        TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
        when(tokenCountEstimator.estimateTokenCountInText("短段。")).thenReturn(3);
        when(tokenCountEstimator.estimateTokenCountInText("这是一个超过阈值的长段落。")).thenReturn(501);
        when(fallbackSplitter.split(any())).thenReturn(List.of(
                TextSegment.from("长段前半"), TextSegment.from("长段后半")));
        DiaryChunker chunker = new DiaryChunker(fallbackSplitter, tokenCountEstimator);

        List<DiaryChunker.DiaryChunk> chunks = chunker.split(diary(), "短段。\n\n这是一个超过阈值的长段落。");

        assertEquals(List.of("短段。", "长段前半", "长段后半"),
                chunks.stream().map(DiaryChunker.DiaryChunk::body).toList());
        assertFalse(chunks.stream().anyMatch(chunk -> chunk.body().isBlank()));
        verify(fallbackSplitter).split(any());
    }

    private Diary diary() {
        return Diary.builder()
                .diaryId("d-1")
                .userId("u-1")
                .entryDate(LocalDate.of(2026, 7, 13))
                .title("海边散步")
                .placeName("青岛")
                .emotion("平静")
                .build();
    }
}
