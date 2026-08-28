package com.aseubel.yusi.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** IrMetrics 纯函数数值验证：recall@k / MRR / nDCG@k 手算已知例。 */
class IrMetricsTest {

    @Test
    void recallAtKCountsDistinctHits() {
        double recall = IrMetrics.recallAtK(List.of("a", "b", "c"),
                List.of("x", "a", "b", "y", "a"), 5);
        assertThat(recall).isEqualTo(IrMetrics.round(2d / 3d));
    }

    @Test
    void recallAtKRespectsCutoff() {
        double recall = IrMetrics.recallAtK(List.of("a", "b"), List.of("a", "x"), 1);
        assertThat(recall).isEqualTo(0.5);
        assertThat(IrMetrics.recallAtK(List.of("a"), List.of("x", "a"), 1)).isZero();
    }

    @Test
    void reciprocalRankFindsFirstRelevant() {
        assertThat(IrMetrics.reciprocalRank(List.of("b"), List.of("x", "y", "b"), 3))
                .isEqualTo(IrMetrics.round(1d / 3));
        assertThat(IrMetrics.reciprocalRank(List.of("z"), List.of("x", "y"), 3)).isZero();
    }

    @Test
    void ndcgUsesGradedGainsAndDiscount() {
        // DCG = (2^1-1)/log2(2) + (2^2-1)/log2(3) = 1 + 3/1.58496 = 2.8928
        // IDCG = 同一相关性集合按最优排序 {2,1}，结果一致 → nDCG = 1
        double perfect = IrMetrics.ndcgAtK(Map.of("a", 1, "b", 2), List.of("b", "a", "x"), 3);
        assertThat(perfect).isEqualTo(1d);

        // 检索顺序最差：k=2 内只有 rel=1 的文档排在 rank2
        double degraded = IrMetrics.ndcgAtK(Map.of("a", 1, "b", 2), List.of("x", "a"), 2);
        // DCG = 1/log2(3) = 0.6309; IDCG 取增益排序 {2,1}：3/log2(2) + 1/log2(3) = 3.631
        assertThat(degraded).isEqualTo(IrMetrics.round(0.63093d / 3.63093d));
    }

    @Test
    void emptyInputsAreHonestZeros() {
        assertThat(IrMetrics.recallAtK(List.of(), List.of("a"), 5)).isZero();
        assertThat(IrMetrics.ndcgAtK(Map.of(), List.of("a"), 5)).isZero();
        assertThat(IrMetrics.average(List.of())).isZero();
    }
}
