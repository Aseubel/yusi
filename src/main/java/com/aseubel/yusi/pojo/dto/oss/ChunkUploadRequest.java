package com.aseubel.yusi.pojo.dto.oss;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadRequest {

    @NotBlank(message = "文件MD5不能为空")
    private String fileMd5;

    @NotNull(message = "分片索引不能为空")
    private Integer chunkIndex;

    @NotNull(message = "分片总数不能为空")
    private Integer totalChunks;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    private String fileName;

    private Long chunkSize;
}
