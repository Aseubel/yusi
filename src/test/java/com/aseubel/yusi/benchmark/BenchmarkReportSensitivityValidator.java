package com.aseubel.yusi.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记分卡红线校验：防止 API key、超长 base64（密钥形态）或整段对话原文进入落盘报告。
 * 仿 EvaluationFixtureRedLineValidator 的"违禁即拒绝"风格。
 */
public class BenchmarkReportSensitivityValidator {

    /** openai 形态 key：sk- 后跟至少 16 位密钥字符。 */
    private static final Pattern API_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{16,}");
    private static final Pattern BEARER_TOKEN = Pattern.compile("Bearer\\s+[A-Za-z0-9._~+/=-]{16,}");
    /** RSA PRIVATE KEY 等块。 */
    private static final Pattern KEY_BLOCK =
            Pattern.compile("BEGIN (RSA )?PRIVATE KEY|BEGIN PUBLIC KEY");
    /** 64 位以上连续 base64 视为疑似密钥材料（记分卡本身只放指标，不应出现）。 */
    private static final Pattern LONG_BASE64 =
            Pattern.compile("[A-Za-z0-9+/]{80,}={0,2}");
    /** 对话原文标记：开启 transcript 导出且单段超过 500 字时视为违规。 */
    private static final String TRANSCRIPT_FIELD_PREFIX = "\"rawTranscript\"";

    public List<String> validate(String reportContent) {
        List<String> violations = new ArrayList<>();
        addIfFind(violations, "api_key_like", API_KEY.matcher(reportContent));
        addIfFind(violations, "bearer_token", BEARER_TOKEN.matcher(reportContent));
        if (KEY_BLOCK.matcher(reportContent).find()) {
            violations.add("private_or_public_key_block");
        }
        Matcher base64Matcher = LONG_BASE64.matcher(reportContent);
        if (base64Matcher.find()) {
            violations.add("long_base64_suspected_secret");
        }
        int index = reportContent.indexOf(TRANSCRIPT_FIELD_PREFIX);
        while (index >= 0) {
            int valueStart = reportContent.indexOf('"', index + TRANSCRIPT_FIELD_PREFIX.length() + 1);
            int valueEnd = valueStart < 0 ? -1 : reportContent.indexOf('"', valueStart + 1);
            if (valueEnd > valueStart && (valueEnd - valueStart) > 500) {
                violations.add("raw_transcript_too_long_at_" + index);
                break;
            }
            index = reportContent.indexOf(TRANSCRIPT_FIELD_PREFIX, index + TRANSCRIPT_FIELD_PREFIX.length());
        }
        return violations;
    }

    private void addIfFind(List<String> violations, String code, Matcher matcher) {
        if (matcher.find()) {
            violations.add(code);
        }
    }
}
