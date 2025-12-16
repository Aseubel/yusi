package com.aseubel.yusi.service.room.impl;

import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.situation.SituationRoom;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SituationReportService {
    public SituationReport analyze(SituationRoom room) {
        List<SituationReport.PersonalSketch> personals = new ArrayList<>();
        for (Map.Entry<String, String> entry : room.getSubmissions().entrySet()) {
            String sketch = buildSketch(entry.getValue());
            personals.add(SituationReport.PersonalSketch.builder().userId(entry.getKey()).sketch(sketch).build());
        }
        List<String> users = new ArrayList<>(room.getMembers());
        List<SituationReport.PairCompatibility> pairs = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            for (int j = i + 1; j < users.size(); j++) {
                String a = users.get(i);
                String b = users.get(j);
                int score = calcScore(room.getSubmissions().get(a), room.getSubmissions().get(b));
                String reason = buildReason(room.getSubmissions().get(a), room.getSubmissions().get(b));
                pairs.add(SituationReport.PairCompatibility.builder().userA(a).userB(b).score(score).reason(reason).build());
            }
        }
        return SituationReport.builder().scenarioId(room.getScenarioId()).personal(personals).pairs(pairs).build();
    }

    private String buildSketch(String narrative) {
        String lower = narrative == null ? "" : narrative.toLowerCase(Locale.ROOT);
        boolean discipline = lower.contains("计划") || lower.contains("规则") || lower.contains("责任");
        boolean fairness = lower.contains("公平") || lower.contains("平衡") || lower.contains("公正");
        boolean empathy = lower.contains("共情") || lower.contains("理解") || lower.contains("感受");
        List<String> traits = new ArrayList<>();
        if (discipline) traits.add("自律");
        if (fairness) traits.add("看重公平");
        if (empathy) traits.add("富有理解");
        if (traits.isEmpty()) traits.add("务实");
        return "一个" + String.join("、", traits) + "的人";
    }

    private int calcScore(String a, String b) {
        Set<String> keywordsA = extractKeywords(a);
        Set<String> keywordsB = extractKeywords(b);
        int overlap = 0;
        for (String k : keywordsA) if (keywordsB.contains(k)) overlap++;
        int base = 50 + overlap * 10;
        return Math.max(0, Math.min(100, base));
    }

    private String buildReason(String a, String b) {
        Set<String> keywordsA = extractKeywords(a);
        Set<String> keywordsB = extractKeywords(b);
        Set<String> common = new HashSet<>(keywordsA);
        common.retainAll(keywordsB);
        if (!common.isEmpty()) {
            return "你们都关注了" + String.join("、", common) + "，在价值取向上更一致。";
        }
        return "你们的叙事关注点不同，但互补可能带来新的视角。";
    }

    private Set<String> extractKeywords(String narrative) {
        String lower = narrative == null ? "" : narrative.toLowerCase(Locale.ROOT);
        List<String> pool = Arrays.asList("责任", "公平", "效率", "情绪", "长期", "短期", "规则", "风险", "群体", "个人");
        Set<String> set = new HashSet<>();
        for (String k : pool) if (lower.contains(k)) set.add(k);
        return set;
    }
}