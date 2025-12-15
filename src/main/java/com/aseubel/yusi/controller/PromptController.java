package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.pojo.entity.PromptTemplate;
import com.aseubel.yusi.service.ai.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prompt")
@Auth // Protect all endpoints
@CrossOrigin("*")
public class PromptController {

    @Autowired
    private PromptService promptService;

    @GetMapping("/{name}")
    public Response<String> getPrompt(@PathVariable String name) {
        return Response.success(promptService.getPrompt(name));
    }

    @PostMapping("/save")
    public Response<PromptTemplate> savePrompt(@RequestBody PromptTemplate prompt) {
        return Response.success(promptService.savePrompt(prompt));
    }

    @PostMapping("/activate")
    public Response<Void> activatePrompt(@RequestParam String name, @RequestParam String version) {
        promptService.activatePrompt(name, version);
        return Response.success();
    }
}
