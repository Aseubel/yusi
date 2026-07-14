package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.Diary;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DiaryChunker {

    private static final int MAX_PARAGRAPH_TOKENS = 500;

    private final DocumentSplitter fallbackSplitter;
    private final TokenCountEstimator tokenCountEstimator;

    public DiaryChunker(DocumentSplitter fallbackSplitter, TokenCountEstimator tokenCountEstimator) {
        this.fallbackSplitter = fallbackSplitter;
        this.tokenCountEstimator = tokenCountEstimator;
    }

    public List<DiaryChunk> split(Diary diary, String plainContent) {
        List<String> bodies = splitParagraphsThenFallback(plainContent);
        String header = buildHeader(diary);
        List<DiaryChunk> chunks = new ArrayList<>(bodies.size());
        for (int index = 0; index < bodies.size(); index++) {
            chunks.add(new DiaryChunk(diary.getDiaryId(), index, bodies.size(), header, bodies.get(index)));
        }
        return chunks;
    }

    private List<String> splitParagraphsThenFallback(String plainContent) {
        List<String> bodies = new ArrayList<>();
        for (String paragraph : plainContent.strip().split("(?:\\R\\s*){2,}")) {
            String normalizedParagraph = paragraph.strip();
            if (normalizedParagraph.isEmpty()) {
                continue;
            }
            if (tokenCountEstimator.estimateTokenCountInText(normalizedParagraph) <= MAX_PARAGRAPH_TOKENS) {
                bodies.add(normalizedParagraph);
                continue;
            }
            for (TextSegment segment : fallbackSplitter.split(Document.document(normalizedParagraph))) {
                String text = segment.text().strip();
                if (!text.isEmpty()) {
                    bodies.add(text);
                }
            }
        }
        return bodies;
    }

    private String buildHeader(Diary diary) {
        List<String> fields = new ArrayList<>();
        if (diary.getEntryDate() != null) {
            fields.add("日期：" + diary.getEntryDate());
        }
        addIfPresent(fields, "标题", diary.getTitle());
        addIfPresent(fields, "地点", diary.getPlaceName());
        addIfPresent(fields, "情绪", diary.getEmotion());
        return String.join("\n", fields);
    }

    private void addIfPresent(List<String> fields, String name, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(name + "：" + value.strip());
        }
    }

    public record DiaryChunk(String diaryId, int index, int count, String header, String body) {
        public String text() {
            return header.isBlank() ? body : header + "\n\n" + body;
        }
    }
}
