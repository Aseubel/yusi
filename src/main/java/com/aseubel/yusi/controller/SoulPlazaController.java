package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.contant.CardType;
import com.aseubel.yusi.pojo.contant.ResonanceType;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.pojo.entity.SoulResonance;
import com.aseubel.yusi.service.plaza.SoulPlazaService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Auth
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plaza")
@CrossOrigin("*")
public class SoulPlazaController {

    private final SoulPlazaService plazaService;

    @Data
    public static class SubmitCardRequest {
        private String content;
        private String originId;
        private CardType type;
    }

    @PostMapping("/submit")
    public Response<SoulCard> submit(@RequestBody SubmitCardRequest request) {
        SoulCard card = plazaService.submitToPlaza(
                UserContext.getUserId(),
                request.getContent(),
                request.getOriginId(),
                request.getType());
        return Response.success(card);
    }

    @GetMapping("/feed")
    public Response<Page<SoulCard>> getFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Response.success(plazaService.getFeed(UserContext.getUserId(), page, size));
    }

    @Data
    public static class ResonateRequest {
        private ResonanceType type;
    }

    @PostMapping("/{cardId}/resonate")
    public Response<SoulResonance> resonate(
            @PathVariable Long cardId,
            @RequestBody ResonateRequest request) {
        return Response.success(plazaService.resonate(
                UserContext.getUserId(),
                cardId,
                request.getType()));
    }
}
