package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.common.repochain.Processor;
import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.common.repochain.Result;
import com.aseubel.yusi.pojo.entity.Diary;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Aseubel
 * @date 2025/5/7 下午1:34
 */
@Slf4j
@Component
public class EmbeddingService implements Processor<Element> {

    @Autowired
    private MilvusEmbeddingStore milvusEmbeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Override
    public Result<Element> process(Element data, int index, ProcessorChain<Element> chain) {
        if (data.getEventType() != EventType.DIARY_WRITE) {
            return chain.process(data, index);
        }
        Diary diary = (Diary) data.getData();
        TextSegment diaryTextSegment = TextSegment.from(diary.getContent(), Metadata.metadata("userId", diary.getUserId()));

        // 转换文本段为Embedding
        Embedding embedding = embeddingModel.embed(diaryTextSegment).content();
        milvusEmbeddingStore.add(diary.getDiaryId(), embedding);

        log.info("存储 {} 的日记的embedding，日记:{}", diary.getUserId(), diary);
        return Result.success(data);
    }
}
