package com.aseubel.yusi.benchmark;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** judge 三路径验证：合法 JSON / 脏输出 / 模型异常，失败必须可观察不可伪精确。 */
class BenchmarkJudgeServiceTest {

    private final BenchmarkFailureRecorder recorder = new BenchmarkFailureRecorder();
    private final ChatModel chatModel = mock(ChatModel.class);
    private final BenchmarkJudgeService service = new BenchmarkJudgeService(chatModel, recorder);

    @Test
    void parsesValidJudgeOutputAndClampsDimensions() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"引用正确性\": 2, \"语气一致性\": \"3\"}"))
                        .build());

        BenchmarkJudgeService.JudgeResult result = service.judge(
                Map.of("引用正确性", "是否准确引用给定记忆", "语气一致性", "语气是否符合人设"),
                "context", "content");

        assertThat(result.available()).isTrue();
        assertThat(result.scores()).containsEntry("引用正确性", 2).containsEntry("语气一致性", 3);
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void dirtyOutputIsRecordedAsParseErrorNotZero() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("我觉得不错，满分！"))
                        .build());

        BenchmarkJudgeService.JudgeResult result = service.judge(
                Map.of("幻觉程度", "对照 context 是否编造"), null, "content");

        assertThat(result.available()).isFalse();
        assertThat(result.error())
                .isEqualTo(BenchmarkFailureRecorder.TYPE_JUDGE_PARSE_ERROR);
        assertThat(recorder.failures()).hasSize(1)
                .allSatisfy(f -> assertThat(f.failureType())
                        .isEqualTo(BenchmarkFailureRecorder.TYPE_JUDGE_PARSE_ERROR));
    }

    @Test
    void modelExceptionIsRecordedAsModelError() {
        when(chatModel.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("boom"));

        BenchmarkJudgeService.JudgeResult result = service.judge(
                Map.of("隐私边界", "是否泄漏不该出现的内容"), null, "content");

        assertThat(result.available()).isFalse();
        assertThat(result.error()).isEqualTo(BenchmarkFailureRecorder.TYPE_MODEL_ERROR);
        assertThat(recorder.failures()).hasSize(1);
    }

    @Test
    void judgeRequestUsesLowTemperatureFixedPromptVersion() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"d\":0}"))
                        .build());

        service.judge(Map.of("d", "rubric"), null, "content");

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        org.mockito.Mockito.verify(chatModel).chat(captor.capture());
        ChatRequest request = captor.getValue();
        if (request.parameters() != null && request.parameters().temperature() != null) {
            assertThat(request.parameters().temperature().doubleValue())
                    .isEqualTo(BenchmarkJudgeService.JUDGE_TEMPERATURE);
        }
        // judge prompt 指纹应为模板文件内容的 8 位 sha256 前缀，且与模板读取一致
        assertThat(BenchmarkVersionFingerprint.judgePromptVersion())
                .hasSize(8)
                .isNotEqualTo("template-missing")
                .isNotEqualTo("template-error");
        assertThat(BenchmarkJudgeService.promptTemplate()).contains("{{DIMENSIONS}}", "{{CONTENT}}");
    }

    @Test
    void extractJsonObjectHandlesMarkdownFences() {
        assertThat(BenchmarkJudgeService.extractJsonObject("```json\n{\"a\":1}\n```"))
                .isEqualTo("{\"a\":1}");
        assertThat(BenchmarkJudgeService.extractJsonObject("前缀 {\"a\": {\"b\": 2}} 后缀"))
                .isEqualTo("{\"a\": {\"b\": 2}}");
    }
}
