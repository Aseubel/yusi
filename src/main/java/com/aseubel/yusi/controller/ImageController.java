package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.pojo.dto.oss.ImageUploadResponse;
import com.aseubel.yusi.service.oss.OssService;
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
