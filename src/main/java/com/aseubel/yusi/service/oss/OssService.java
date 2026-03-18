package com.aseubel.yusi.service.oss;

import com.aseubel.yusi.config.oss.OssProperties;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.UuidUtils;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.*;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OSSClient ossClient;
    private final OssProperties ossProperties;

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    public String uploadImage(MultipartFile file, String userId) {
        validateImageFile(file);
        
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String objectKey = ossProperties.getImageFolder() + userId + "/" + 
            UuidUtils.genUuidSimple() + extension;
        
        try {
            byte[] bytes = file.getBytes();
            
            PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(objectKey)
                .body(BinaryData.fromBytes(bytes))
                .contentType(file.getContentType())
                .build();
            
            PutObjectResult response = ossClient.putObject(request);
            log.info("Image uploaded successfully: {}", objectKey);
            
            return objectKey;
        } catch (IOException e) {
            log.error("Failed to upload image: {}", objectKey, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片上传失败");
        }
    }

    public List<String> uploadImages(List<MultipartFile> files, String userId) {
        List<String> objectKeys = new ArrayList<>();
        for (MultipartFile file : files) {
            objectKeys.add(uploadImage(file, userId));
        }
        return objectKeys;
    }

    public String generatePresignedUrl(String objectKey) {
        return generatePresignedUrl(objectKey, ossProperties.getUrlExpireSeconds());
    }

    public String generatePresignedUrl(String objectKey, int expireSeconds) {
        try {
            String url = "https://" + ossProperties.getBucketName() + "." + 
                ossProperties.getEndpoint() + "/" + objectKey;
            log.debug("Generated URL for: {}", objectKey);
            return url;
        } catch (Exception e) {
            log.error("Failed to generate URL for: {}", objectKey, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成图片访问链接失败");
        }
    }

    public List<String> generatePresignedUrls(List<String> objectKeys) {
        List<String> urls = new ArrayList<>();
        for (String objectKey : objectKeys) {
            urls.add(generatePresignedUrl(objectKey));
        }
        return urls;
    }

    public void deleteImage(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.newBuilder()
            .bucket(ossProperties.getBucketName())
            .key(objectKey)
            .build();
        
        ossClient.deleteObject(request);
        log.info("Image deleted: {}", objectKey);
    }

    public void deleteImages(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            deleteImage(objectKey);
        }
    }

    public String getObjectKeyFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        int bucketIndex = url.indexOf(ossProperties.getBucketName() + "/");
        if (bucketIndex == -1) {
            return url;
        }
        return url.substring(bucketIndex + ossProperties.getBucketName().length() + 1);
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片文件不能为空");
        }
        
        if (file.getSize() > ossProperties.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, 
                "图片大小超过限制: " + (ossProperties.getMaxFileSize() / 1024 / 1024) + "MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, 
                "不支持的图片格式，仅支持: JPEG, PNG, GIF, WebP, BMP");
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    public void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片key不能为空");
        }
        if (objectKey.contains("..") || objectKey.contains("/..") || objectKey.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的图片key");
        }
        if (!objectKey.startsWith(ossProperties.getImageFolder())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的图片路径");
        }
    }

    public void validateObjectKeys(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        for (String objectKey : objectKeys) {
            validateObjectKey(objectKey);
        }
    }
}
