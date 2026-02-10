package com.aseubel.yusi.service.lifegraph;

import cn.hutool.core.util.StrUtil;
import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.repository.LifeGraphEntityRepository;
import com.aseubel.yusi.service.lifegraph.dto.LifeGraphMergeSuggestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LifeGraphMergeSuggestionService {

    private final LifeGraphEntityRepository entityRepository;

    public List<LifeGraphMergeSuggestion> suggest(String userId, int limit) {
        if (StrUtil.isBlank(userId) || limit <= 0) {
            return List.of();
        }
        List<LifeGraphEntity> entities = entityRepository.findTop50ByUserIdOrderByMentionCountDesc(userId);
        List<LifeGraphMergeSuggestion> suggestions = new ArrayList<>();

        for (int i = 0; i < entities.size(); i++) {
            LifeGraphEntity a = entities.get(i);
            if (a.getType() == LifeGraphEntity.EntityType.User) {
                continue;
            }
            for (int j = i + 1; j < entities.size(); j++) {
                LifeGraphEntity b = entities.get(j);
                if (b.getType() != a.getType()) {
                    continue;
                }
                double score = similarity(a.getNameNorm(), b.getNameNorm());
                if (score >= 0.85) {
                    suggestions.add(LifeGraphMergeSuggestion.builder()
                            .entityIdA(a.getId())
                            .entityIdB(b.getId())
                            .nameA(a.getDisplayName())
                            .nameB(b.getDisplayName())
                            .type(a.getType().name())
                            .score(score)
                            .build());
                    if (suggestions.size() >= limit) {
                        return suggestions;
                    }
                }
            }
        }

        return suggestions;
    }

    private double similarity(String a, String b) {
        if (StrUtil.isBlank(a) || StrUtil.isBlank(b)) {
            return 0.0;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.equalsIgnoreCase(y)) {
            return 1.0;
        }
        if (x.contains(y) || y.contains(x)) {
            int min = Math.min(x.length(), y.length());
            int max = Math.max(x.length(), y.length());
            return 0.85 + 0.15 * ((double) min / (double) max);
        }
        int d = levenshtein(x, y);
        int maxLen = Math.max(x.length(), y.length());
        if (maxLen == 0) {
            return 1.0;
        }
        return 1.0 - ((double) d / (double) maxLen);
    }

    private int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
