package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.Auth;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.dto.soulplaza.ResonateRequest;
import com.aseubel.yusi.pojo.dto.soulplaza.SubmitCardRequest;
import com.aseubel.yusi.pojo.dto.soulplaza.UpdateCardRequest;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.pojo.entity.SoulResonance;
import com.aseubel.yusi.service.plaza.SoulPlazaService;
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
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String emotion) {
        return Response.success(plazaService.getFeed(UserContext.getUserId(), page, size, emotion));
    }

    @GetMapping("/my")
    public Response<Page<SoulCard>> getMyCards(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Response.success(plazaService.getMyCards(UserContext.getUserId(), page, size));
    }

    @PutMapping("/{cardId}")
    public Response<SoulCard> updateCard(
            @PathVariable Long cardId,
            @RequestBody UpdateCardRequest request) {
        return Response.success(plazaService.updateCard(UserContext.getUserId(), cardId, request.getContent()));
    }

    @DeleteMapping("/{cardId}")
    public Response<Void> deleteCard(@PathVariable Long cardId) {
        plazaService.deleteCard(UserContext.getUserId(), cardId);
        return Response.success(null);
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
