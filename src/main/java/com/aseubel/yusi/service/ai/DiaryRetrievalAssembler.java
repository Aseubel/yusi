package com.aseubel.yusi.service.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DiaryRetrievalAssembler {

    public List<String> assemble(List<SearchResp.SearchResult> hits, int resultLimit) {
        List<ResultGroup> groups = new ArrayList<>();
        Map<String, DiaryResultGroup> groupsByDiaryId = new LinkedHashMap<>();

        for (SearchResp.SearchResult result : hits) {
            DiaryHit hit = parseHit(result);
            if (hit == null) {
                groups.add(new StandaloneResultGroup(textOf(result)));
                continue;
            }
            DiaryResultGroup group = groupsByDiaryId.get(hit.diaryId());
            if (group == null) {
                group = new DiaryResultGroup(hit);
                groupsByDiaryId.put(hit.diaryId(), group);
                groups.add(group);
            } else {
                group.add(hit);
            }
        }

        return groups.stream()
                .limit(resultLimit)
                .map(ResultGroup::toContext)
                .toList();
    }

    private DiaryHit parseHit(SearchResp.SearchResult result) {
        Object metadataValue = result.getEntity().get("metadata");
        MetadataValues metadata = metadataValues(metadataValue);
        if (metadata == null) {
            return null;
        }
        String diaryId = asString(metadata.diaryId());
        Integer chunkIndex = asInteger(metadata.chunkIndex());
        Integer chunkCount = asInteger(metadata.chunkCount());
        if (diaryId == null || diaryId.isBlank() || chunkIndex == null || chunkCount == null
                || chunkIndex < 0 || chunkCount <= chunkIndex) {
            return null;
        }
        return new DiaryHit(diaryId, chunkIndex, textOf(result));
    }

    private MetadataValues metadataValues(Object value) {
        if (value instanceof Map<?, ?> metadata) {
            return new MetadataValues(metadata.get("diaryId"), metadata.get("chunkIndex"),
                    metadata.get("chunkCount"));
        }
        if (value instanceof JsonObject metadata) {
            return new MetadataValues(metadata.get("diaryId"), metadata.get("chunkIndex"),
                    metadata.get("chunkCount"));
        }
        return null;
    }

    private String asString(Object value) {
        if (value instanceof JsonElement element) {
            return element.isJsonPrimitive() ? element.getAsString() : null;
        }
        return value instanceof String text ? text : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof JsonElement element) {
            if (!element.isJsonPrimitive()) {
                return null;
            }
            try {
                return element.getAsInt();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String textOf(SearchResp.SearchResult result) {
        Object text = result.getEntity().get("text");
        return text == null ? "" : text.toString();
    }

    private interface ResultGroup {
        String toContext();
    }

    private record StandaloneResultGroup(String text) implements ResultGroup {
        @Override
        public String toContext() {
            return text;
        }
    }

    private static final class DiaryResultGroup implements ResultGroup {
        private final DiaryHit anchor;
        private final Map<Integer, DiaryHit> hitsByIndex = new LinkedHashMap<>();

        private DiaryResultGroup(DiaryHit anchor) {
            this.anchor = anchor;
            add(anchor);
        }

        private void add(DiaryHit hit) {
            hitsByIndex.putIfAbsent(hit.chunkIndex(), hit);
        }

        @Override
        public String toContext() {
            List<DiaryHit> adjacentHits = hitsByIndex.values().stream()
                    .filter(hit -> Math.abs(hit.chunkIndex() - anchor.chunkIndex()) <= 1)
                    .sorted(Comparator.comparingInt(DiaryHit::chunkIndex))
                    .toList();
            return mergeHeaderOnce(adjacentHits);
        }

        private String mergeHeaderOnce(List<DiaryHit> hits) {
            String[] first = splitHeaderAndBody(hits.getFirst().text());
            StringBuilder context = new StringBuilder(first[0]);
            for (DiaryHit hit : hits) {
                String[] parts = splitHeaderAndBody(hit.text());
                if (!parts[1].isEmpty()) {
                    if (!context.isEmpty()) {
                        context.append("\n\n");
                    }
                    context.append(parts[1]);
                }
            }
            return context.toString();
        }

        private String[] splitHeaderAndBody(String text) {
            int separator = text.indexOf("\n\n");
            return separator < 0 ? new String[] { "", text }
                    : new String[] { text.substring(0, separator), text.substring(separator + 2) };
        }
    }

    private record DiaryHit(String diaryId, int chunkIndex, String text) {
    }

    private record MetadataValues(Object diaryId, Object chunkIndex, Object chunkCount) {
    }
}
