package com.aseubel.yusi.service.lifegraph.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSnapshotDTO {

    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private long totalNodeCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeDTO {
        private Long id;
        private String displayName;
        private String type;
        private Integer mentionCount;
        private String summary;
        private String props;
        private Long version;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkDTO {
        private Long id;
        private Long sourceId;
        private Long targetId;
        private String type;
        private BigDecimal confidence;
        private Integer weight;
        private Long version;
    }
}
