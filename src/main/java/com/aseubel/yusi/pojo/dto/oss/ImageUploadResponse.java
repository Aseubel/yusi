package com.aseubel.yusi.pojo.dto.oss;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {

    private String objectKey;

    private String url;

    private String fileName;

    private Long fileSize;

    private String contentType;
}
