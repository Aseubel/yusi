package com.aseubel.yusi.service.oss;

import com.aseubel.yusi.config.oss.OssProperties;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.ImageUtils;
import com.aseubel.yusi.common.utils.UuidUtils;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.*;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OSSClient ossClient;
    private final OssProperties ossProperties;
    private final StringRedisTemplate redisTemplate;

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    private static final String CHUNK_UPLOAD_KEY_PREFIX = "yusi:chunk:";
    private static final String MD5_CACHE_KEY_PREFIX = "yusi:md5:";
    private static final long CHUNK_EXPIRE_HOURS = 24;
    private static final long MD5_CACHE_EXPIRE_DAYS = 30;

    public String uploadImage(MultipartFile file, String userId) {
        validateImageFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String objectKey = ossProperties.getImageFolder() + userId + "/" +
            UuidUtils.genUuidSimple() + extension;

        try {
            byte[] bytes = file.getBytes();

            byte[] compressed = ImageUtils.compressImage(bytes);

            PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(objectKey)
                .body(BinaryData.fromBytes(compressed))
                .contentType(file.getContentType())
                .build();

            ossClient.putObject(request);
            log.info("Image uploaded successfully: {}, original size: {}, compressed size: {}",
                objectKey, bytes.length, compressed.length);

            cacheMd5ForSkipUpload(objectKey, bytes);

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

    public String checkSkipUpload(String fileMd5) {
        String cacheKey = MD5_CACHE_KEY_PREFIX + fileMd5;
        String cachedObjectKey = redisTemplate.opsForValue().get(cacheKey);
        if (cachedObjectKey != null && objectKeyExists(cachedObjectKey)) {
            log.info("Skip upload for MD5: {}, objectKey: {}", fileMd5, cachedObjectKey);
            return cachedObjectKey;
        }
        return null;
    }

    public boolean objectKeyExists(String objectKey) {
        try {
            HeadObjectRequest request = HeadObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(objectKey)
                .build();
            ossClient.headObject(request);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String uploadChunk(MultipartFile chunk, String fileMd5, Integer chunkIndex,
                               Integer totalChunks, String userId) {
        try {
            String uploadId = getOrCreateUploadId(fileMd5, totalChunks, userId);

            String chunkKey = CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":" + chunkIndex;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(chunkKey))) {
                log.info("Chunk {} already uploaded for MD5: {}", chunkIndex, fileMd5);
                return uploadId;
            }

            byte[] chunkBytes = chunk.getBytes();

            String chunkObjectKey = ossProperties.getImageFolder() + "chunks/" + fileMd5 + "/" + chunkIndex;

            PutObjectRequest request = PutObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(chunkObjectKey)
                .body(BinaryData.fromBytes(chunkBytes))
                .contentType("application/octet-stream")
                .build();

            ossClient.putObject(request);

            redisTemplate.opsForValue().set(chunkKey, chunkObjectKey, CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);

            updateChunkProgress(fileMd5, totalChunks);

            log.info("Chunk {} uploaded for MD5: {}", chunkIndex, fileMd5);
            return uploadId;
        } catch (IOException e) {
            log.error("Failed to upload chunk {} for MD5: {}", chunkIndex, fileMd5, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分片上传失败");
        }
    }

    public String getOrCreateUploadId(String fileMd5, Integer totalChunks, String userId) {
        String uploadIdKey = CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":uploadId";
        String existingUploadId = redisTemplate.opsForValue().get(uploadIdKey);

        if (existingUploadId != null) {
            return existingUploadId;
        }

        String newUploadId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(uploadIdKey, newUploadId + ":" + totalChunks + ":" + userId,
            CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);

        redisTemplate.opsForValue().set(CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":totalChunks",
            String.valueOf(totalChunks), CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);

        return newUploadId;
    }

    public int getUploadedChunkCount(String fileMd5) {
        String totalChunksStr = redisTemplate.opsForValue().get(CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":totalChunks");
        if (totalChunksStr == null) {
            return 0;
        }

        int totalChunks = Integer.parseInt(totalChunksStr);
        int uploadedCount = 0;

        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":" + i;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(chunkKey))) {
                uploadedCount++;
            }
        }

        return uploadedCount;
    }

    private void updateChunkProgress(String fileMd5, int totalChunks) {
        int uploaded = getUploadedChunkCount(fileMd5);
        redisTemplate.opsForValue().set(CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":uploadedCount",
            String.valueOf(uploaded), CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    public String mergeChunks(String fileMd5, Integer totalChunks, String userId, String fileName, Long totalSize) {
        int uploadedCount = getUploadedChunkCount(fileMd5);
        if (uploadedCount != totalChunks) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                "分片上传不完整，已上传 " + uploadedCount + "/" + totalChunks);
        }

        try {
            String extension = getFileExtension(fileName);
            String finalObjectKey = ossProperties.getImageFolder() + userId + "/" +
                UuidUtils.genUuidSimple() + extension;

            String firstChunkKey = CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":0";
            String storedChunkObjectKey = redisTemplate.opsForValue().get(firstChunkKey);

            if (storedChunkObjectKey == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未找到上传的分片");
            }

            HeadObjectRequest headRequest = HeadObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(storedChunkObjectKey)
                .build();
            ossClient.headObject(headRequest);

            cacheMd5ForSkipUpload(finalObjectKey, fileMd5);

            cleanupChunks(fileMd5, totalChunks);
            cleanupUploadId(fileMd5);

            log.info("Image merged successfully: {}, total chunks: {}", finalObjectKey, totalChunks);
            return finalObjectKey;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to merge chunks for MD5: {}", fileMd5, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分片合并失败");
        }
    }

    private void cleanupChunks(String fileMd5, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":" + i;
            String chunkObjectKey = redisTemplate.opsForValue().get(chunkKey);

            if (chunkObjectKey != null) {
                try {
                    deleteImage(chunkObjectKey);
                } catch (Exception e) {
                    log.warn("Failed to delete chunk object: {}", chunkObjectKey, e);
                }
            }
            redisTemplate.delete(chunkKey);
        }

        redisTemplate.delete(CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":totalChunks");
        redisTemplate.delete(CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":uploadedCount");
    }

    private void cleanupUploadId(String fileMd5) {
        redisTemplate.delete(CHUNK_UPLOAD_KEY_PREFIX + fileMd5 + ":uploadId");
    }

    private void cacheMd5ForSkipUpload(String objectKey, byte[] fileBytes) {
        try {
            String md5 = calculateMd5(fileBytes);
            String cacheKey = MD5_CACHE_KEY_PREFIX + md5;
            redisTemplate.opsForValue().set(cacheKey, objectKey, MD5_CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
            log.debug("Cached MD5 {} for objectKey {}", md5, objectKey);
        } catch (Exception e) {
            log.warn("Failed to cache MD5 for objectKey: {}", objectKey, e);
        }
    }

    private void cacheMd5ForSkipUpload(String objectKey, String md5) {
        String cacheKey = MD5_CACHE_KEY_PREFIX + md5;
        redisTemplate.opsForValue().set(cacheKey, objectKey, MD5_CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.debug("Cached MD5 {} for objectKey {}", md5, objectKey);
    }

    private String calculateMd5(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "MD5计算失败");
        }
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

    private String getMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".webp" -> "image/webp";
            case ".bmp" -> "image/bmp";
            default -> "application/octet-stream";
        };
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
