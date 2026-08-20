package com.aseubel.yusi.service.oss;

import com.aseubel.yusi.config.oss.OssProperties;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.ImageUtils;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
import com.aseubel.yusi.common.utils.UuidUtils;
import com.aseubel.yusi.pojo.entity.ImageFile;
import com.aseubel.yusi.repository.ImageFileRepository;
import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.models.*;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OSSClient ossClient;
    private final OssProperties ossProperties;
    private final StringRedisTemplate redisTemplate;
    private final ImageFileRepository imageFileRepository;

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final List<String> ALLOWED_AUDIO_TYPES = List.of(
            "audio/mpeg", "audio/mp3", "audio/mpga", "audio/mp4", "audio/x-m4a",
            "audio/wav", "audio/x-wav", "audio/ogg", "audio/webm", "video/webm");

    private static final String CHUNK_UPLOAD_KEY_PREFIX = "yusi:chunk:";
    private static final String MD5_CACHE_KEY_PREFIX = "yusi:md5:";
    private static final long CHUNK_EXPIRE_HOURS = 24;
    private static final long MD5_CACHE_EXPIRE_DAYS = 30;
    private static final int MAX_CHUNKS = 1000;
    private static final int MAX_URL_EXPIRE_SECONDS = 24 * 60 * 60;
    private static final long MAX_CHUNK_SIZE = 5L * 1024 * 1024;

    public String uploadImage(MultipartFile file, String userId) {
        validateImageFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String objectKey = ossProperties.getImageFolder() + userId + "/" +
                UuidUtils.genUuidSimple() + extension;

        try {
            byte[] bytes = file.getBytes();

            byte[] compressed = ImageUtils.compressImage(bytes);

            String fileMd5 = calculateMd5(compressed);

            var existingFile = imageFileRepository.findByFileMd5AndUserId(fileMd5, userId);
            if (existingFile.isPresent()) {
                String existObjectKey = existingFile.get().getObjectKey();
                if (objectKeyExists(existObjectKey)) {
                    log.info("OSS upload skipped: operation=oss_upload, category=image, reason=existing_object");
                    saveImageFileAsync(existObjectKey, fileMd5, userId, originalFilename, (long) compressed.length,
                            file.getContentType());
                    return existObjectKey;
                }
            }

            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossProperties.getBucketName())
                    .key(objectKey)
                    .body(BinaryData.fromBytes(compressed))
                    .contentType(file.getContentType())
                    .build();

            ossClient.putObject(request);
            log.info("OSS upload completed: operation=oss_upload, category=image, outcome=success");

            cacheMd5ForSkipUpload(objectKey, fileMd5, userId);

            saveImageFileAsync(objectKey, fileMd5, userId, originalFilename, (long) compressed.length,
                    file.getContentType());

            return objectKey;
        } catch (IOException e) {
            log.error("OSS upload failed: operation=oss_upload, category=image, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
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

    public String uploadAudio(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "音频文件不能为空");
        }
        if (file.getSize() > ossProperties.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "音频文件超过大小限制");
        }
        String contentType = file.getContentType();
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)
                .split(";", 2)[0].trim();
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        boolean supportedByType = ALLOWED_AUDIO_TYPES.contains(normalizedContentType);
        boolean supportedByExtension = (contentType == null || contentType.isBlank()
                || "application/octet-stream".equals(normalizedContentType))
                && List.of("mp3", "mpga", "m4a", "mp4", "wav", "ogg", "webm", "aac", "amr").contains(extension);
        if (!supportedByType && !supportedByExtension) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的音频格式");
        }

        String objectKey = ossProperties.getAudioFolder() + userId + "/"
                + UuidUtils.genUuidSimple() + extension;
        try {
            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossProperties.getBucketName())
                    .key(objectKey)
                    .body(BinaryData.fromBytes(file.getBytes()))
                    .contentType(contentType)
                    .build();
            ossClient.putObject(request);
            return objectKey;
        } catch (IOException e) {
            log.error("OSS upload failed: operation=oss_upload, category=audio, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "音频上传失败");
        }
    }

    private String generatePresignedUrl(String objectKey) {
        return generatePresignedUrl(objectKey, ossProperties.getUrlExpireSeconds());
    }

    private String generatePresignedUrl(String objectKey, int expireSeconds) {
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的 OSS 路径");
        }
        if (expireSeconds < 1 || expireSeconds > MAX_URL_EXPIRE_SECONDS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "OSS 链接有效期不合法");
        }
        try {
            GetObjectRequest request = GetObjectRequest.newBuilder()
                    .bucket(ossProperties.getBucketName())
                    .key(objectKey)
                    .build();
            String url = ossClient.presign(request, PresignOptions.newBuilder()
                    .expiration(Duration.ofSeconds(expireSeconds))
                    .build()).url();
            log.debug("OSS presign completed: operation=oss_presign, category=object, outcome=success");
            return url;
        } catch (Exception e) {
            log.error("OSS presign failed: operation=oss_presign, category=object, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成图片访问链接失败");
        }
    }

    private List<String> generatePresignedUrls(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.size() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片数量超出限制");
        }
        List<String> urls = new ArrayList<>();
        for (String objectKey : objectKeys) {
            urls.add(generatePresignedUrl(objectKey));
        }
        return urls;
    }

    public String generateOwnedUrl(String objectKey, String userId) {
        validateOwnedObjectKey(objectKey, userId);
        return generatePresignedUrl(objectKey);
    }

    public List<String> generateOwnedUrls(List<String> objectKeys, String userId) {
        if (objectKeys == null || objectKeys.size() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片数量超出限制");
        }
        return objectKeys.stream().map(key -> generateOwnedUrl(key, userId)).toList();
    }

    private void deleteImage(String objectKey) {
        deleteObject(objectKey);
    }

    public void deleteOwnedImage(String objectKey, String userId) {
        validateOwnedObjectKey(objectKey, userId);
        deleteObject(objectKey);
    }

    public void deleteOwnedAudioObject(String objectKey, String userId) {
        validateOwnedAudioObjectKey(objectKey, userId);
        deleteObject(objectKey);
    }

    public void deleteOwnedChunkObject(String objectKey, String userId) {
        validateOwnedChunkObjectKey(objectKey, userId);
        deleteObject(objectKey);
    }

    public void deleteOwnedImages(List<String> objectKeys, String userId) {
        if (objectKeys == null || objectKeys.size() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片数量超出限制");
        }
        objectKeys.forEach(key -> deleteOwnedImage(key, userId));
    }

    private void deleteObject(String objectKey) {
        DeleteObjectRequest request = DeleteObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(objectKey)
                .build();

        ossClient.deleteObject(request);
        log.info("Object deleted: operation=oss_delete, category=owned_object");
    }

    private void deleteImages(List<String> objectKeys) {
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

    public String checkSkipUpload(String fileMd5, String userId) {
        validateFileMd5(fileMd5);
        var imageFile = imageFileRepository.findByFileMd5AndUserId(fileMd5, userId);
        if (imageFile.isPresent()) {
            String objectKey = imageFile.get().getObjectKey();
            if (objectKeyExists(objectKey)) {
                log.info("OSS upload skipped: operation=oss_upload, category=image, reason=database_reference");
                return objectKey;
            }
        }

        String cacheKey = MD5_CACHE_KEY_PREFIX + userId + ":" + fileMd5;
        String cachedObjectKey = redisTemplate.opsForValue().get(cacheKey);
        if (cachedObjectKey != null && objectKeyExists(cachedObjectKey)) {
            log.info("OSS upload skipped: operation=oss_upload, category=image, reason=cache_reference");
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
        validateChunkRequest(chunk, fileMd5, chunkIndex, totalChunks, userId);
        String uploadId = getOrCreateUploadId(fileMd5, totalChunks, userId);

        String chunkKey = chunkKey(fileMd5, userId, chunkIndex);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(chunkKey))) {
            log.info("OSS chunk upload skipped: operation=oss_chunk_upload, category=chunk_exists");
            return uploadId;
        }

        String reservationKey = chunkKey + ":reserved";
        Boolean reserved = redisTemplate.opsForValue().setIfAbsent(
                reservationKey, "1", CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);
        if (!Boolean.TRUE.equals(reserved)) {
            return uploadId;
        }

        String bytesKey = chunkPrefix(fileMd5, userId) + ":bytes";
        long chunkSize = chunk.getSize();
        boolean bytesCounted = false;
        try {
            Long totalBytes = redisTemplate.opsForValue().increment(bytesKey, chunkSize);
            bytesCounted = totalBytes != null;
            if (totalBytes == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分片计数失败");
            }
            redisTemplate.expire(bytesKey, CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);
            if (totalBytes > ossProperties.getMaxFileSize()) {
                redisTemplate.opsForValue().increment(bytesKey, -chunkSize);
                bytesCounted = false;
                throw new BusinessException(ErrorCode.PARAM_ERROR, "分片总大小超过限制");
            }

            byte[] chunkBytes = chunk.getBytes();
            String chunkObjectKey = chunkObjectKey(fileMd5, userId, chunkIndex);

            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossProperties.getBucketName())
                    .key(chunkObjectKey)
                    .body(BinaryData.fromBytes(chunkBytes))
                    .contentType("application/octet-stream")
                    .build();

            ossClient.putObject(request);

            redisTemplate.opsForValue().set(chunkKey, chunkObjectKey, CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(chunkKey + ":size", String.valueOf(chunkSize),
                    CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);

            updateChunkProgress(fileMd5, userId);

            log.info("OSS chunk upload completed: operation=oss_chunk_upload, category=chunk, outcome=success");
            return uploadId;
        } catch (IOException e) {
            if (bytesCounted) {
                redisTemplate.opsForValue().increment(bytesKey, -chunkSize);
            }
            log.error("OSS chunk upload failed: operation=oss_chunk_upload, category=chunk, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分片上传失败");
        } catch (RuntimeException e) {
            if (bytesCounted) {
                redisTemplate.opsForValue().increment(bytesKey, -chunkSize);
            }
            throw e;
        } finally {
            redisTemplate.delete(reservationKey);
        }
    }

    public String getOrCreateUploadId(String fileMd5, Integer totalChunks, String userId) {
        validateFileMd5(fileMd5);
        validateUserId(userId);
        if (totalChunks == null || totalChunks < 1 || totalChunks > MAX_CHUNKS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分片数量不合法");
        }
        String uploadIdKey = chunkPrefix(fileMd5, userId) + ":uploadId";
        String existingUploadId = redisTemplate.opsForValue().get(uploadIdKey);

        if (existingUploadId != null) {
            String[] metadata = existingUploadId.split(":", 3);
            if (metadata.length != 3 || !userId.equals(metadata[2])
                    || !String.valueOf(totalChunks).equals(metadata[1])) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "分片会话参数不一致");
            }
            return metadata[0];
        }

        String newUploadId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(uploadIdKey, newUploadId + ":" + totalChunks + ":" + userId,
                CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);

        redisTemplate.opsForValue().set(chunkPrefix(fileMd5, userId) + ":totalChunks",
                String.valueOf(totalChunks), CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);

        return newUploadId;
    }

    public int getUploadedChunkCount(String fileMd5, String userId) {
        validateFileMd5(fileMd5);
        validateUserId(userId);
        String totalChunksStr = redisTemplate.opsForValue().get(chunkPrefix(fileMd5, userId) + ":totalChunks");
        if (totalChunksStr == null) {
            return 0;
        }

        int totalChunks = Integer.parseInt(totalChunksStr);
        int uploadedCount = 0;

        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = chunkKey(fileMd5, userId, i);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(chunkKey))) {
                uploadedCount++;
            }
        }

        return uploadedCount;
    }

    private void updateChunkProgress(String fileMd5, String userId) {
        int uploaded = getUploadedChunkCount(fileMd5, userId);
        redisTemplate.opsForValue().set(chunkPrefix(fileMd5, userId) + ":uploadedCount",
                String.valueOf(uploaded), CHUNK_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    public String mergeChunks(String fileMd5, Integer totalChunks, String userId, String fileName, Long totalSize) {
        validateFileMd5(fileMd5);
        validateUserId(userId);
        if (totalSize == null || totalSize < 1 || totalSize > ossProperties.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件大小不合法");
        }
        if (totalChunks == null || totalChunks < 1 || totalChunks > MAX_CHUNKS) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分片数量不合法");
        }
        String extension = validateImageFileName(fileName);
        validateUploadSession(fileMd5, totalChunks, userId);
        int uploadedCount = getUploadedChunkCount(fileMd5, userId);
        if (uploadedCount != totalChunks) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "分片上传不完整，已上传 " + uploadedCount + "/" + totalChunks);
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("yusi-merge-");

            Path mergedFile = tempDir.resolve("merged.bin");
            long mergedSize = 0;

            try (OutputStream output = Files.newOutputStream(mergedFile)) {
                for (int i = 0; i < totalChunks; i++) {
                    String chunkObjectKey = chunkObjectKey(fileMd5, userId, i);
                    mergedSize += downloadChunk(chunkObjectKey, output, ossProperties.getMaxFileSize() - mergedSize);
                }
            }

            if (mergedSize != totalSize) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "合并文件大小与声明不一致");
            }
            if (mergedSize > ossProperties.getMaxFileSize()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "合并文件超过大小限制");
            }

            byte[] mergedBytes = Files.readAllBytes(mergedFile);

            String finalObjectKey = ossProperties.getImageFolder() + userId + "/" +
                    UuidUtils.genUuidSimple() + extension;

            byte[] compressedBytes = ImageUtils.compressImage(mergedBytes);

            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(ossProperties.getBucketName())
                    .key(finalObjectKey)
                    .body(BinaryData.fromBytes(compressedBytes))
                    .contentType(getMimeType(extension))
                    .build();

            ossClient.putObject(request);

            cacheMd5ForSkipUpload(finalObjectKey, fileMd5, userId);

            saveImageFileAsync(finalObjectKey, fileMd5, userId, fileName, (long) compressedBytes.length,
                    getMimeType(extension));

            cleanupChunks(fileMd5, totalChunks, userId);
            cleanupUploadId(fileMd5, userId);

            log.info("OSS chunk merge completed: operation=oss_chunk_merge, category=image, outcome=success");
            return finalObjectKey;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OSS chunk merge failed: operation=oss_chunk_merge, category=image, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "分片合并失败");
        } finally {
            if (tempDir != null) {
                try {
                    try (var paths = Files.walk(tempDir)) {
                        paths
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    }
                } catch (IOException e) {
                    log.warn("OSS temp cleanup failed: operation=oss_temp_cleanup, category=local_directory, exceptionType={}",
                            LowSensitivityLogSummary.exceptionType(e));
                }
            }
        }
    }

    private long downloadChunk(String objectKey, OutputStream output, long remainingBytes) throws Exception {
        GetObjectRequest request = GetObjectRequest.newBuilder()
                .bucket(ossProperties.getBucketName())
                .key(objectKey)
                .build();

        try (GetObjectResult result = ossClient.getObject(request);
                InputStream input = result.body()) {
            long total = 0;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > remainingBytes) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "合并文件超过大小限制");
                }
                output.write(buffer, 0, read);
            }
            return total;
        }
    }

    private void cleanupChunks(String fileMd5, int totalChunks, String userId) {
        for (int i = 0; i < totalChunks; i++) {
            String chunkKey = chunkKey(fileMd5, userId, i);
            String chunkObjectKey = redisTemplate.opsForValue().get(chunkKey);

            if (chunkObjectKey != null) {
                try {
                    deleteImage(chunkObjectKey);
                } catch (Exception e) {
                    log.warn("OSS chunk cleanup failed: operation=oss_chunk_cleanup, category=object, exceptionType={}",
                            LowSensitivityLogSummary.exceptionType(e));
                }
            }
            redisTemplate.delete(chunkKey);
            redisTemplate.delete(chunkKey + ":reserved");
            redisTemplate.delete(chunkKey + ":size");
        }

        redisTemplate.delete(chunkPrefix(fileMd5, userId) + ":totalChunks");
        redisTemplate.delete(chunkPrefix(fileMd5, userId) + ":uploadedCount");
        redisTemplate.delete(chunkPrefix(fileMd5, userId) + ":bytes");
    }

    private void cleanupUploadId(String fileMd5, String userId) {
        redisTemplate.delete(chunkPrefix(fileMd5, userId) + ":uploadId");
    }

    @Async("threadPoolExecutor")
    public void saveImageFileAsync(String objectKey, String fileMd5, String userId, String fileName,
            Long fileSize, String contentType) {
        try {
            if (imageFileRepository.existsByFileMd5AndUserId(fileMd5, userId)) {
                log.debug("Image metadata save skipped: operation=oss_image_metadata, category=duplicate");
                return;
            }

            ImageFile imageFile = ImageFile.builder()
                    .fileMd5(fileMd5)
                    .objectKey(objectKey)
                    .userId(userId)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .contentType(contentType)
                    .createTime(LocalDateTime.now())
                    .build();

            imageFileRepository.save(imageFile);
            log.debug("Image metadata saved: operation=oss_image_metadata, category=image, outcome=success");
        } catch (Exception e) {
            log.error("Image metadata save failed: operation=oss_image_metadata, category=image, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
        }
    }

    private void cacheMd5ForSkipUpload(String objectKey, String md5, String userId) {
        String cacheKey = MD5_CACHE_KEY_PREFIX + userId + ":" + md5;
        redisTemplate.opsForValue().set(cacheKey, objectKey, MD5_CACHE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.debug("OSS upload cache updated: operation=oss_upload_cache, category=md5_reference");
    }

    private String calculateMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
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

    private String validateImageFileName(String filename) {
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.contains("/") || filename.contains("\\") || filename.endsWith(".")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片文件名不合法");
        }
        String extension = getFileExtension(filename);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的图片扩展名");
        }
        return extension;
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

    private void validateObjectKey(String objectKey) {
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

    private void validateObjectKeys(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        for (String objectKey : objectKeys) {
            validateObjectKey(objectKey);
        }
    }

    public void validateOwnedObjectKeys(List<String> objectKeys, String userId) {
        if (objectKeys == null || objectKeys.size() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "图片数量超出限制");
        }
        for (String objectKey : objectKeys) {
            validateOwnedObjectKey(objectKey, userId);
        }
    }

    public void validateOwnedAudioObjectKey(String objectKey, String userId) {
        if (objectKey == null || userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的音频路径");
        }
        if (objectKey.contains("..") || objectKey.contains("\\")
                || !objectKey.startsWith(ossProperties.getAudioFolder() + userId + "/")) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该音频");
        }
    }

    public String generateOwnedAudioUrl(String objectKey, String userId) {
        validateOwnedAudioObjectKey(objectKey, userId);
        return generatePresignedUrl(objectKey);
    }

    private void validateOwnedObjectKey(String objectKey, String userId) {
        if (objectKey == null || userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的图片路径");
        }
        validateObjectKey(objectKey);
        String ownerPrefix = ossProperties.getImageFolder() + userId + "/";
        if (!objectKey.startsWith(ownerPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该图片");
        }
    }

    private void validateOwnedChunkObjectKey(String objectKey, String userId) {
        if (objectKey == null || userId == null || userId.isBlank()
                || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的分片路径");
        }
        String ownerPrefix = ossProperties.getImageFolder() + "chunks/" + userId + "/";
        if (!objectKey.startsWith(ownerPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该分片");
        }
    }

    private void validateChunkRequest(MultipartFile chunk, String fileMd5, Integer chunkIndex,
            Integer totalChunks, String userId) {
        long maxChunkSize = Math.min(ossProperties.getMaxFileSize(), MAX_CHUNK_SIZE);
        if (chunk == null || chunk.isEmpty() || chunk.getSize() > maxChunkSize) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分片文件不合法");
        }
        validateFileMd5(fileMd5);
        validateUserId(userId);
        if (totalChunks == null || totalChunks < 1 || totalChunks > MAX_CHUNKS
                || chunkIndex == null || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分片参数不合法");
        }
    }

    private void validateFileMd5(String fileMd5) {
        if (fileMd5 == null || !fileMd5.matches("[a-fA-F0-9]{32}")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件 MD5 不合法");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank() || userId.contains(":") || userId.contains("/")
                || userId.contains("\\")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户身份不合法");
        }
    }

    private void validateUploadSession(String fileMd5, int totalChunks, String userId) {
        String metadata = redisTemplate.opsForValue().get(chunkPrefix(fileMd5, userId) + ":uploadId");
        if (metadata == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分片上传会话不存在或已过期");
        }
        String[] parts = metadata.split(":", 3);
        if (parts.length != 3 || !String.valueOf(totalChunks).equals(parts[1]) || !userId.equals(parts[2])) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "分片会话参数不一致");
        }
    }

    private String chunkPrefix(String fileMd5, String userId) {
        return CHUNK_UPLOAD_KEY_PREFIX + userId + ":" + fileMd5;
    }

    private String chunkKey(String fileMd5, String userId, int chunkIndex) {
        return chunkPrefix(fileMd5, userId) + ":" + chunkIndex;
    }

    private String chunkObjectKey(String fileMd5, String userId, int chunkIndex) {
        return ossProperties.getImageFolder() + "chunks/" + userId + "/" + fileMd5 + "/" + chunkIndex;
    }
}
