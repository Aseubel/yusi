package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.ratelimit.LimitType;
import com.aseubel.yusi.common.ratelimit.RateLimiter;
import com.aseubel.yusi.pojo.dto.oss.*;
import com.aseubel.yusi.service.oss.OssService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Auth
@RestController
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageController {

    private final OssService ossService;

    @PostMapping("/upload")
    @RateLimiter(key = "image-upload", time = 60, count = 20, limitType = LimitType.USER)
    public Response<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) String ignoredUserId) {

        String userId = UserContext.getUserId();
        String objectKey = ossService.uploadImage(file, userId);
        String url = ossService.generateOwnedUrl(objectKey, userId);

        ImageUploadResponse response = ImageUploadResponse.builder()
                .objectKey(objectKey)
                .url(url)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .build();

        return Response.success(response);
    }

    @PostMapping("/upload/batch")
    @RateLimiter(key = "image-upload-batch", time = 60, count = 5, limitType = LimitType.USER)
    public Response<List<ImageUploadResponse>> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "userId", required = false) String ignoredUserId) {

        String userId = UserContext.getUserId();
        List<ImageUploadResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            String objectKey = ossService.uploadImage(file, userId);
            String url = ossService.generateOwnedUrl(objectKey, userId);

            responses.add(ImageUploadResponse.builder()
                    .objectKey(objectKey)
                    .url(url)
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .build());
        }

        return Response.success(responses);
    }

    @GetMapping("/check")
    @RateLimiter(key = "image-upload-check", time = 60, count = 60, limitType = LimitType.USER)
    public Response<ImageUploadCheckResponse> checkUpload(
            @RequestParam("fileMd5") String fileMd5) {

        String userId = UserContext.getUserId();
        String objectKey = ossService.checkSkipUpload(fileMd5, userId);

        ImageUploadCheckResponse response = ImageUploadCheckResponse.builder()
                .skip(objectKey != null)
                .objectKey(objectKey)
                .url(objectKey != null ? ossService.generateOwnedUrl(objectKey, userId) : null)
                .fileMd5(fileMd5)
                .build();

        return Response.success(response);
    }

    @PostMapping("/chunk/upload")
    @RateLimiter(key = "image-chunk-upload", time = 60, count = 120, limitType = LimitType.USER)
    public Response<ChunkUploadResponse> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam(value = "userId", required = false) String ignoredUserId) {

        String userId = UserContext.getUserId();
        String uploadId = ossService.uploadChunk(chunk, fileMd5, chunkIndex, totalChunks, userId);
        int uploadedChunks = ossService.getUploadedChunkCount(fileMd5, userId);

        ChunkUploadResponse response = ChunkUploadResponse.builder()
                .uploadId(uploadId)
                .chunkIndex(chunkIndex)
                .uploaded(uploadedChunks == totalChunks)
                .uploadedChunks(uploadedChunks)
                .totalChunks(totalChunks)
                .build();

        return Response.success(response);
    }

    @PostMapping("/chunk/merge")
    @RateLimiter(key = "image-chunk-merge", time = 60, count = 20, limitType = LimitType.USER)
    public Response<ImageUploadResponse> mergeChunks(
            @Valid @RequestBody MergeChunksRequest request) {

        String objectKey = ossService.mergeChunks(
                request.getFileMd5(),
                request.getTotalChunks(),
                UserContext.getUserId(),
                request.getFileName(),
                request.getTotalSize());

        String url = ossService.generateOwnedUrl(objectKey, UserContext.getUserId());

        ImageUploadResponse response = ImageUploadResponse.builder()
                .objectKey(objectKey)
                .url(url)
                .fileName(request.getFileName())
                .fileSize(request.getTotalSize())
                .contentType("image/jpeg")
                .build();

        return Response.success(response);
    }

    @GetMapping("/chunk/progress")
    @RateLimiter(key = "image-chunk-progress", time = 60, count = 120, limitType = LimitType.USER)
    public Response<ChunkUploadResponse> getChunkProgress(
            @RequestParam("fileMd5") String fileMd5) {

        int uploadedChunks = ossService.getUploadedChunkCount(fileMd5, UserContext.getUserId());

        ChunkUploadResponse response = ChunkUploadResponse.builder()
                .uploadedChunks(uploadedChunks)
                .build();

        return Response.success(response);
    }

    @GetMapping("/url")
    @RateLimiter(key = "image-url", time = 60, count = 60, limitType = LimitType.USER)
    public Response<String> getPresignedUrl(@RequestParam("objectKey") String objectKey) {
        String url = ossService.generateOwnedUrl(objectKey, UserContext.getUserId());
        return Response.success(url);
    }

    @PostMapping("/urls")
    @RateLimiter(key = "image-urls", time = 60, count = 60, limitType = LimitType.USER)
    public Response<List<String>> getPresignedUrls(@RequestBody List<String> objectKeys) {
        List<String> urls = ossService.generateOwnedUrls(objectKeys, UserContext.getUserId());
        return Response.success(urls);
    }

    @DeleteMapping
    @RateLimiter(key = "image-delete", time = 60, count = 30, limitType = LimitType.USER)
    public Response<Void> deleteImage(@RequestParam("objectKey") String objectKey) {
        ossService.deleteOwnedImage(objectKey, UserContext.getUserId());
        return Response.success();
    }

    @DeleteMapping("/batch")
    @RateLimiter(key = "image-delete-batch", time = 60, count = 5, limitType = LimitType.USER)
    public Response<Void> deleteImages(@RequestBody List<String> objectKeys) {
        ossService.deleteOwnedImages(objectKeys, UserContext.getUserId());
        return Response.success();
    }
}
