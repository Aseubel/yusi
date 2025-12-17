package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.common.repochain.Processor;
import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.common.repochain.Result;
import com.aseubel.yusi.pojo.entity.Diary;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * @author Aseubel
 * @date 2025/5/7 下午1:34
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingService implements Processor<Element> {


    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentSplitter documentSplitter;

    @Override
    public Result<Element> process(Element data, int index, ProcessorChain<Element> chain) {
        if (data.getEventType() != EventType.DIARY_WRITE && data.getEventType() != EventType.DIARY_MODIFY) {
            return chain.process(data, index);
        }
        Diary diary = (Diary) data.getData();
        HashMap<String, Object> params = new HashMap<>();
        params.put("userId", diary.getUserId());

        String text = String.format("日期：%s\n日记内容：%s", params.get("entryDate"), diary.getContent());
        Document document = Document.document(text, Metadata.from(params));

        // 切分文本段
        List<TextSegment> diaryTextSegment = documentSplitter.split(document);

        // 移除旧的embedding
        milvusEmbeddingStore.remove(diary.getDiaryId());
        // 转换文本段为Embedding
        List<Embedding> embedding = embeddingModel.embedAll(diaryTextSegment).content();
        // 这里三个都要用，暴露的方法也只有addAll有三个入参
        // 因为用户可能修改内容，所以diaryId也得记录
        milvusEmbeddingStore.addAll(Collections.singletonList(diary.getDiaryId()), embedding, diaryTextSegment);

        log.info("存储 {} 的日记的embedding，日记:{}", diary.getUserId(), diary);
        return Result.success(data);
    }
}
