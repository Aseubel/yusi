package com.aseubel.yusi.pojo.dto.oss;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MergeChunksRequest {

    @NotBlank(message = "文件MD5不能为空")
    private String fileMd5;

    @NotNull(message = "分片总数不能为空")
    private Integer totalChunks;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名过长")
    private String fileName;

    private Long totalSize;
}
