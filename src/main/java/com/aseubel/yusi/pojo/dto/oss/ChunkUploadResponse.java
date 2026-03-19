package com.aseubel.yusi.pojo.dto.oss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadResponse {

    private String uploadId;

    private Integer chunkIndex;

    private boolean uploaded;

    private Integer uploadedChunks;

    private Integer totalChunks;
}
