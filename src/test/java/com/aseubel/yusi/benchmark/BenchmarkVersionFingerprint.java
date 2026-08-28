package com.aseubel.yusi.benchmark;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 记分卡版本指纹：改了 Prompt / 换模型 / 换 embedding 后，两次记分卡必须可以通过指纹解释分数变化。
 */
public final class BenchmarkVersionFingerprint {

    private BenchmarkVersionFingerprint() {
    }

    /**
     * judge 提示词版本 = 模板文件内容 sha256 前 8 位。
     * 模板外置于 benchmark/judge-prompt-template.txt，修改后指纹自动变化，无需手动递增。
     */
    static String judgePromptVersion() {
        try (var stream = BenchmarkVersionFingerprint.class.getClassLoader()
                .getResourceAsStream("benchmark/judge-prompt-template.txt")) {
            if (stream == null) {
                return "template-missing";
            }
            byte[] bytes = stream.readAllBytes();
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            return "template-error";
        }
    }

    /**
     * @param routingSchemaVersion yusi.routing.schema-version
     * @param embeddingModelName   真实使用的 embedding 模型名（来自 dev/benchmark 配置）
     * @param chatModelName        路由 chat 主模型名（judge 与业务共用，见 benchmark plan 决策）
     * @param judgeTemperature     judge 固定温度常量（写入 ChatRequestParameters）
     */
    public static Map<String, String> collect(String routingSchemaVersion,
            String embeddingModelName, String chatModelName, double judgeTemperature) {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("gitSha", gitSha());
        versions.put("routing", routingSchemaVersion == null ? "unknown" : routingSchemaVersion);
        versions.put("model", chatModelName == null ? "unknown" : chatModelName);
        versions.put("embedding", embeddingModelName == null ? "unknown" : embeddingModelName);
        versions.put("judgeModel", chatModelName == null ? "unknown" : chatModelName);
        versions.put("judgePrompt", judgePromptVersion());
        versions.put("judgeTemperature", String.valueOf(judgeTemperature));
        versions.put("fixtures", BenchmarkEnv.FIXTURES_VERSION);
        return java.util.Collections.unmodifiableMap(versions);
    }

    private static String gitSha() {
        String fromEnv = System.getenv("YUSI_BENCHMARK_GIT_SHA");
        return fromEnv == null || fromEnv.isBlank() ? "unknown" : fromEnv.trim();
    }
}
