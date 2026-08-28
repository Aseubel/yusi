package com.aseubel.yusi.benchmark;

import java.util.List;
import java.util.Map;

/**
 * 信息检索指标纯函数实现：recall@k、MRR、nDCG@k。
 * 相关性等级支持二值（0/1）与分级（0/1/2/...）两种。
 */
public final class IrMetrics {

    private static final double ROUND_FACTOR = 10_000d;

    private IrMetrics() {
    }

    /**
     * recall@k = 命中的相关文档数 / 相关文档总数。
     *
     * @param relevantIds 标注为相关的文档 ID 集合
     * @param retrieved   系统返回的文档 ID 序列（按排名）
     * @param k           截断位置
     */
    public static double recallAtK(List<String> relevantIds, List<String> retrieved, int k) {
        if (relevantIds == null || relevantIds.isEmpty()) {
            return 0d;
        }
        long hits = retrieved.stream()
                .limit(k)
                .filter(relevantIds::contains)
                .distinct()
                .count();
        return round((double) hits / relevantIds.size());
    }

    /**
     * 单条查询的 MRR：第一个相关文档排名的倒数；无相关结果则为 0。
     */
    public static double reciprocalRank(List<String> relevantIds, List<String> retrieved, int k) {
        for (int index = 0; index < Math.min(k, retrieved.size()); index++) {
            if (relevantIds.contains(retrieved.get(index))) {
                return round(1d / (index + 1));
            }
        }
        return 0d;
    }

    /**
     * nDCG@k：使用分级相关性增益 2^rel - 1，折损 log2(rank + 1)。
     *
     * @param gradedRelevance docId -> 相关性等级（>=1 即相关）
     */
    public static double ndcgAtK(Map<String, Integer> gradedRelevance, List<String> retrieved, int k) {
        double dcg = 0d;
        for (int index = 0; index < Math.min(k, retrieved.size()); index++) {
            Integer relevance = gradedRelevance.get(retrieved.get(index));
            if (relevance != null && relevance > 0) {
                dcg += (Math.pow(2, relevance) - 1) / log2(index + 2);
            }
        }
        double idcg = idealDcg(gradedRelevance.values().stream().toList(), k);
        return idcg == 0d ? 0d : round(dcg / idcg);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    private static double idealDcg(List<Integer> relevances, int k) {
        List<Integer> sorted = relevances.stream()
                .sorted(java.util.Comparator.reverseOrder())
                .limit(k)
                .toList();
        double dcg = 0d;
        for (int index = 0; index < sorted.size(); index++) {
            int relevance = sorted.get(index);
            if (relevance > 0) {
                dcg += (Math.pow(2, relevance) - 1) / log2(index + 2);
            }
        }
        return dcg;
    }

    public static double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0d;
        }
        return round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0d));
    }

    public static double round(double value) {
        return Math.round(value * ROUND_FACTOR) / ROUND_FACTOR;
    }
}
