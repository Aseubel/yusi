package com.aseubel.yusi.service.lifegraph.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LifeGraphExtractionResult {
    private List<ExtractedEntity> entities;
    private List<ExtractedRelation> relations;
    private List<ExtractedMention> mentions;

    @Data
    public static class ExtractedEntity {
        private String type;
        private String displayName;
        private String nameNorm;
        private List<String> aliases;
        private Double confidence;
        private Map<String, Object> props;
    }

    @Data
    public static class ExtractedRelation {
        private String source;
        private String target;
        private String type;
        private Double confidence;
        private Map<String, Object> props;
        private String evidenceSnippet;
    }

    @Data
    public static class ExtractedMention {
        private String entity;
        private String snippet;
        private Map<String, Object> props;
    }
}
