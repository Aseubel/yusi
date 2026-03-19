package com.aseubel.yusi.pojo.dto.oss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadCheckResponse {

    private boolean skip;

    private String objectKey;

    private String url;

    private String fileMd5;

    private Long fileSize;
}
