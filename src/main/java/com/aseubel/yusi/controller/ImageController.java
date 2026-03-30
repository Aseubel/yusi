package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
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
@CrossOrigin("*")
@RequestMapping("/api/image")
@RequiredArgsConstructor
public class ImageController {

    private final OssService ossService;

    @PostMapping("/upload")
    public Response<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {

        String objectKey = ossService.uploadImage(file, userId);
        String url = ossService.generatePresignedUrl(objectKey);

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
    public Response<List<ImageUploadResponse>> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("userId") String userId) {

        List<ImageUploadResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            String objectKey = ossService.uploadImage(file, userId);
            String url = ossService.generatePresignedUrl(objectKey);

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
    public Response<ImageUploadCheckResponse> checkUpload(
            @RequestParam("fileMd5") String fileMd5) {

        String objectKey = ossService.checkSkipUpload(fileMd5);

        ImageUploadCheckResponse response = ImageUploadCheckResponse.builder()
                .skip(objectKey != null)
                .objectKey(objectKey)
                .url(objectKey != null ? ossService.generatePresignedUrl(objectKey) : null)
                .fileMd5(fileMd5)
                .build();

        return Response.success(response);
    }

    @PostMapping("/chunk/upload")
    public Response<ChunkUploadResponse> uploadChunk(
            @RequestParam("file") MultipartFile chunk,
            @RequestParam("fileMd5") String fileMd5,
            @RequestParam("chunkIndex") Integer chunkIndex,
            @RequestParam("totalChunks") Integer totalChunks,
            @RequestParam("userId") String userId) {

        String uploadId = ossService.uploadChunk(chunk, fileMd5, chunkIndex, totalChunks, userId);
        int uploadedChunks = ossService.getUploadedChunkCount(fileMd5);

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
    public Response<ImageUploadResponse> mergeChunks(
            @Valid @RequestBody MergeChunksRequest request) {

        String objectKey = ossService.mergeChunks(
                request.getFileMd5(),
                request.getTotalChunks(),
                request.getUserId(),
                request.getFileName(),
                request.getTotalSize());

        String url = ossService.generatePresignedUrl(objectKey);

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
    public Response<ChunkUploadResponse> getChunkProgress(
            @RequestParam("fileMd5") String fileMd5) {

        int uploadedChunks = ossService.getUploadedChunkCount(fileMd5);

        ChunkUploadResponse response = ChunkUploadResponse.builder()
                .uploadedChunks(uploadedChunks)
                .build();

        return Response.success(response);
    }

    @Auth(required = false)
    @GetMapping("/url")
    public Response<String> getPresignedUrl(@RequestParam("objectKey") String objectKey) {
        String url = ossService.generatePresignedUrl(objectKey);
        return Response.success(url);
    }

    @PostMapping("/urls")
    public Response<List<String>> getPresignedUrls(@RequestBody List<String> objectKeys) {
        List<String> urls = ossService.generatePresignedUrls(objectKeys);
        return Response.success(urls);
    }

    @DeleteMapping
    public Response<Void> deleteImage(@RequestParam("objectKey") String objectKey) {
        ossService.deleteImage(objectKey);
        return Response.success();
    }

    @DeleteMapping("/batch")
    public Response<Void> deleteImages(@RequestBody List<String> objectKeys) {
        ossService.deleteImages(objectKeys);
        return Response.success();
    }
}
