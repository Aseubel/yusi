package com.aseubel.yusi.service.ai;

import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.EmbeddingTask;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.EmbeddingTaskRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.diary.DiaryService;
import com.google.gson.JsonObject;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingBatchServiceTest {

    @Mock
    private EmbeddingTaskRepository taskRepository;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MilvusClientV2 milvusClientV2;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private DiaryChunker diaryChunker;
    @Mock
    private DiaryService diaryService;

    @Test
    void processPendingTasks_writesDiaryChunkMetadataAndContextualText() {
        EmbeddingBatchService service = new EmbeddingBatchService(taskRepository, diaryRepository, userRepository,
                milvusClientV2, embeddingModel, diaryChunker, diaryService);
        EmbeddingTask task = EmbeddingTask.createUpsertTask("diary-1", "user-1");
        task.setId(1L);
        Diary diary = Diary.builder().diaryId("diary-1").userId("user-1")
                .entryDate(LocalDate.of(2026, 7, 13)).title("测试日记")
                .plainContent("第一段。\n\n第二段。")
                .build();
        List<DiaryChunker.DiaryChunk> chunks = List.of(
                new DiaryChunker.DiaryChunk("diary-1", 0, 2, "日期：2026-07-13\n标题：测试日记", "第一段。"),
                new DiaryChunker.DiaryChunk("diary-1", 1, 2, "日期：2026-07-13\n标题：测试日记", "第二段。"));

        when(taskRepository.findPendingTasksForUpdate(any(), anyInt())).thenReturn(List.of(task));
        when(diaryRepository.findByDiaryId("diary-1")).thenReturn(diary);
        when(userRepository.findByUserId("user-1")).thenReturn(User.builder().keyMode("DEFAULT").build());
        when(diaryChunker.split(diary, diary.getPlainContent())).thenReturn(chunks);
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(
                Embedding.from(new float[] { 0.1f }), Embedding.from(new float[] { 0.2f }))));

        service.processPendingTasks();

        ArgumentCaptor<InsertReq> captor = ArgumentCaptor.forClass(InsertReq.class);
        verify(milvusClientV2).insert(captor.capture());
        List<JsonObject> rows = captor.getValue().getData();
        assertEquals(2, rows.size());
        assertEquals("diary-1", rows.get(0).getAsJsonObject("metadata").get("diaryId").getAsString());
        assertEquals(0, rows.get(0).getAsJsonObject("metadata").get("chunkIndex").getAsInt());
        assertEquals(2, rows.get(1).getAsJsonObject("metadata").get("chunkCount").getAsInt());
        assertTrue(rows.get(0).get("text").getAsString().contains("标题：测试日记"));
    }
}
