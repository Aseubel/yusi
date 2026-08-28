package com.aseubel.yusi.benchmark;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一记分卡权重配置（application-benchmark.yml → yusi.benchmark.scorecard）。
 * 权重不写死：未配置、和不为 1 或与聚合层不匹配时，记分卡只报分项并注明原因，不合成 overallScore。
 */
@Data
@ConfigurationProperties(prefix = "yusi.benchmark.scorecard")
public class BenchmarkScorecardProperties {

    /** 层名 → 权重；键须与各 layer runner 写入的 aggregateScores 键一致。 */
    private Map<String, Double> weights = new LinkedHashMap<>();
}
