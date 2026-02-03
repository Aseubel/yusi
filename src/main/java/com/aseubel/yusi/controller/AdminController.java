package com.aseubel.yusi.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.service.user.UserService;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import groovy.util.logging.Slf4j;
import lombok.RequiredArgsConstructor;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
@CrossOrigin("*")
public class AdminController {
    
    private final UserService userService;
    private final MilvusEmbeddingStore milvusEmbeddingStore;

    @PostMapping("/remove-diary-collection")
    public void removeDiaryCollection() {
        String userId = UserContext.getUserId();
        if(userService.checkAdmin(userId)) {
            milvusEmbeddingStore.removeAll();
        } else {
            throw new BusinessException("Only admin can remove diary collection");
        }
    }
}
