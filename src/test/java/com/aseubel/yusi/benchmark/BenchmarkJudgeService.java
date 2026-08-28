package com.aseubel.yusi.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM-as-judge：固定 rubric 提示词 + 固定温度（低温），要求模型只输出 JSON {@code {dim:0..3}}。
 * 三种失败路径（合法 JSON / 脏输出 / 超时或模型异常）都通过 {@link JudgeResult#available()} 诚实暴露，
 * 解析失败同时写入 FailureRecorder。
 */
public class BenchmarkJudgeService {

    public static final double JUDGE_TEMPERATURE = 0.1d;
    /** 判分档位提示词约束：整数 0-3，禁止小数与文字说明。 */
    static final String SCALE_INSTRUCTION =
            "评分档位为整数 0-3（0=完全不满足，3=完全满足）。";

    /**
     * judge 提示词模板外置于 classpath:benchmark/judge-prompt-template.txt（不得在代码里硬编码文案），
     * 占位符：{{SCALE}} / {{DIMENSIONS}} / {{CONTEXT_BLOCK}} / {{CONTENT}} / {{OUTPUT_FORMAT}}。
     * 读入时统一换行符，保证跨机器（local/server）生成内容与指纹一致。
     */
    private static final String PROMPT_TEMPLATE = loadTemplate();

    static String promptTemplate() {
        return PROMPT_TEMPLATE;
    }

    private static String loadTemplate() {
        try (var stream = BenchmarkJudgeService.class.getClassLoader()
                .getResourceAsStream("benchmark/judge-prompt-template.txt")) {
            if (stream == null) {
                throw new IllegalStateException("missing benchmark/judge-prompt-template.txt on classpath");
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("failed to load judge prompt template", e);
        }
    }

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final BenchmarkFailureRecorder failureRecorder;

    public BenchmarkJudgeService(ChatModel chatModel, BenchmarkFailureRecorder failureRecorder) {
        this.chatModel = chatModel;
        this.failureRecorder = failureRecorder;
    }

    /**
     * judge 结果：available=false 表示该维度本次不可用（分数不得参与平均）。
     */
    public record JudgeResult(Map<String, Integer> scores, boolean available, String error) {
        public static JudgeResult unavailable(String error) {
            return new JudgeResult(Map.of(), false, error);
        }

        public double average() {
            return scores.values().stream().mapToInt(Integer::intValue).average().orElse(0d);
        }
    }

    /**
     * 对一段被评文本按维度 rubric 打分。
     *
     * @param dimensions    维度名 -> 该维度的中文 rubric 说明（判分标准）
     * @param context       给 judge 的参考上下文（如预置记忆、gold 要点）；可为 null
     * @param content       待评对象正文（模型回答 / 推荐信等）
     */
    public JudgeResult judge(Map<String, String> dimensions, String context, String content) {
        if (dimensions == null || dimensions.isEmpty()) {
            throw new IllegalArgumentException("judge dimensions must not be empty");
        }
        String prompt = buildPrompt(dimensions, context, content);
        String step = "judge";
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(UserMessage.from(prompt)))
                    .parameters(ChatRequestParameters.builder().temperature(JUDGE_TEMPERATURE).build())
                    .build();
            ChatResponse response = failureRecorder.withinTimeout(
                    BenchmarkEnv.stepTimeoutSeconds(step), () -> chatModel.chat(request));
            if (response == null || response.aiMessage() == null
                    || response.aiMessage().text() == null || response.aiMessage().text().isBlank()) {
                failureRecorder.record(step, BenchmarkFailureRecorder.TYPE_MODEL_ERROR, "empty judge response");
                return JudgeResult.unavailable("MODEL_ERROR");
            }
            return parse(response.aiMessage().text(), dimensions.keySet(), step);
        } catch (java.util.concurrent.TimeoutException e) {
            failureRecorder.record(step, BenchmarkFailureRecorder.TYPE_TIMEOUT, "judge timeout");
            return JudgeResult.unavailable(BenchmarkFailureRecorder.TYPE_TIMEOUT);
        } catch (RuntimeException e) {
            failureRecorder.record(step, BenchmarkFailureRecorder.TYPE_MODEL_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            return JudgeResult.unavailable(BenchmarkFailureRecorder.TYPE_MODEL_ERROR);
        }
    }

    /** 解析模型输出；脏输出走 JUDGE_PARSE_ERROR 而不是伪精确。 */
    JudgeResult parse(String rawOutput, java.util.Set<String> expectedDimensions, String step) {
        try {
            String json = extractJsonObject(rawOutput);
            JsonNode root = MAPPER.readTree(json);
            Map<String, Integer> scores = new LinkedHashMap<>();
            for (String dimension : expectedDimensions) {
                JsonNode value = root.get(dimension);
                if (value == null || !value.isValueNode()) {
                    throw new IllegalArgumentException("missing or non-scalar dimension: " + dimension);
                }
                // 允许数字或数字字符串；小数会被 parseInt 拒绝并归为 JUDGE_PARSE_ERROR
                int score = Integer.parseInt(value.asText().trim());
                if (score < 0 || score > 3) {
                    throw new IllegalArgumentException("dimension out of range 0-3: " + dimension);
                }
                scores.put(dimension, score);
            }
            return new JudgeResult(java.util.Collections.unmodifiableMap(scores), true, null);
        } catch (Exception e) {
            failureRecorder.record(step, BenchmarkFailureRecorder.TYPE_JUDGE_PARSE_ERROR,
                    BenchmarkFailureRecorder.LowRiskMessages.describe(e));
            return JudgeResult.unavailable(BenchmarkFailureRecorder.TYPE_JUDGE_PARSE_ERROR);
        }
    }

    /** 从可能包裹 ```json``` 或带解释文字的输出中截取第一个 {...}。 */
    static String extractJsonObject(String output) {
        String trimmed = output.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("no json object found in judge output");
        }
        return trimmed.substring(start, end + 1);
    }

    private String buildPrompt(Map<String, String> dimensions, String context, String content) {
        StringBuilder dimensionLines = new StringBuilder();
        dimensions.forEach((name, rubric) -> dimensionLines.append("- ").append(name).append(": ")
                .append(rubric).append('\n'));
        String contextBlock = context != null && !context.isBlank()
                ? "评审参考资料：\n" + context + '\n'
                : "";
        String outputFormat = "输出格式要求：只输出 JSON，形如 {"
                + String.join(",", dimensions.keySet()) + "}，键值均为 0-3 整数，不要输出任何其他文字。";
        return PROMPT_TEMPLATE
                .replace("{{SCALE}}", SCALE_INSTRUCTION)
                .replace("{{DIMENSIONS}}", dimensionLines.toString())
                .replace("{{CONTEXT_BLOCK}}", contextBlock)
                .replace("{{CONTENT}}", content == null ? "" : content)
                .replace("{{OUTPUT_FORMAT}}", outputFormat);
    }
}
