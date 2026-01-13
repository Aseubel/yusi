package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.common.repochain.Processor;
import com.aseubel.yusi.common.repochain.ProcessorChain;
import com.aseubel.yusi.common.repochain.Result;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.UserRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UserRepository userRepository;

    /**
     * 检查用户是否允许 RAG 功能
     * - DEFAULT 模式：允许
     * - CUSTOM 模式 + 云端备份：允许
     * - CUSTOM 模式 + 无备份：不允许（最高隐私）
     */
    private boolean isRagAllowed(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user == null) {
            return false;
        }

        String keyMode = user.getKeyMode();
        if (keyMode == null || "DEFAULT".equals(keyMode)) {
            return true;
        }

        // CUSTOM 模式：仅当开启云端备份时允许 RAG
        return Boolean.TRUE.equals(user.getHasCloudBackup());
    }

    @Override
    public Result<Element> process(Element data, int index, ProcessorChain<Element> chain) {
        if (data.getEventType() != EventType.DIARY_WRITE && data.getEventType() != EventType.DIARY_MODIFY) {
            return chain.process(data, index);
        }

        Diary diary = (Diary) data.getData();

        // 检查用户隐私设置
        if (!isRagAllowed(diary.getUserId())) {
            log.info("用户 {} 使用 CUSTOM 密钥模式且未开启云端备份，跳过向量化入库", diary.getUserId());
            // 如果是修改操作，也要删除可能存在的旧 embedding
            if (data.getEventType() == EventType.DIARY_MODIFY) {
                try {
                    milvusEmbeddingStore.remove(diary.getDiaryId());
                    log.info("已删除用户 {} 的日记 {} 的旧 embedding", diary.getUserId(), diary.getDiaryId());
                } catch (Exception e) {
                    log.warn("删除旧 embedding 失败", e);
                }
            }
            return chain.process(data, index);
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("userId", diary.getUserId());
        // 添加 entryDate 作为独立的 Metadata 字段，格式 YYYY-MM-DD，用于时间范围过滤
        params.put("entryDate", diary.getEntryDate().toString());

        // 优先使用 plainContent（前端发送的明文），如果不存在则使用 content
        // plainContent 用于客户端加密场景，content 用于非加密或服务端加密场景
        String text = diary.getPlainContent();
        if (text == null || text.isEmpty()) {
            text = diary.getContent();
        }

        if (text == null || text.isEmpty()) {
            log.warn("日记 {} 内容为空，跳过向量化", diary.getDiaryId());
            return chain.process(data, index);
        }

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
        return chain.process(data, index);
    }
}
