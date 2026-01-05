package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.pojo.dto.situation.*;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.service.room.SituationRoomService;
import com.aseubel.yusi.common.auth.Auth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Auth
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/room")
public class SituationRoomController {

    @Autowired
    private SituationRoomService situationRoomService;

    @PostMapping("/create")
    public Response<SituationRoom> create(@RequestBody CreateRoomRequest request) {
        SituationRoom room = situationRoomService.createRoom(request.getOwnerId(),
                Math.max(2, Math.min(8, request.getMaxMembers())));
        return Response.success(room);
    }

    @PostMapping("/join")
    public Response<SituationRoom> join(@RequestBody JoinRoomRequest request) {
        SituationRoom room = situationRoomService.joinRoom(request.getCode(), request.getUserId());
        return Response.success(room);
    }

    @PostMapping("/start")
    public Response<SituationRoom> startRoom(@RequestBody StartRoomRequest request) {
        SituationRoom room = situationRoomService.startRoom(request.getCode(), request.getScenarioId(),
                request.getOwnerId());
        return Response.success(room);
    }

    @PostMapping("/scenarios/submit")
    public Response<SituationScenario> submitScenario(@RequestBody SubmitScenarioRequest request) {
        return Response.success(situationRoomService.submitScenario(UserContext.getUserId(), request.getTitle(), request.getDescription()));
    }

    @PostMapping("/scenarios/review")
    public Response<SituationScenario> reviewScenario(@RequestBody ReviewScenarioRequest request) {
        return Response.success(situationRoomService.reviewScenario(UserContext.getUserId(), request.getScenarioId(), request.getStatus(), request.getRejectReason()));
    }

    @GetMapping("/scenarios")
    public Response<java.util.List<SituationScenario>> getScenarios() {
        return Response.success(situationRoomService.getScenarios());
    }

    @PostMapping("/cancel")
    public Response<?> cancelRoom(@RequestBody JoinRoomRequest request) {
        // Reusing JoinRoomRequest for code + userId
        situationRoomService.cancelRoom(request.getCode(), request.getUserId());
        return Response.success();
    }

    @PostMapping("/vote-cancel")
    public Response<SituationRoom> voteCancel(@RequestBody JoinRoomRequest request) {
        SituationRoom room = situationRoomService.voteCancel(request.getCode(), request.getUserId());
        return Response.success(room);
    }

    @PostMapping("/submit")
    public Response<SituationRoom> submitNarrative(@RequestBody SubmitNarrativeRequest request) {
        SituationRoom room = situationRoomService.submit(request.getCode(), request.getUserId(),
                request.getNarrative(), request.getIsPublic());
        return Response.success(room);
    }

    @GetMapping("/history")
    public Response<java.util.List<SituationRoom>> getHistory() {
        return Response.success(situationRoomService.getHistory(UserContext.getUserId()));
    }

    @GetMapping("/report/{code}")
    public Response<SituationReport> report(@PathVariable("code") String code) {
        SituationReport report = situationRoomService.getReport(code);
        return Response.success(report);
    }

    @GetMapping("/{code}")
    public Response<SituationRoom> getRoom(@PathVariable("code") String code) {
        // Use getRoomDetail to return masked data
        SituationRoom room = situationRoomService.getRoomDetail(code, UserContext.getUserId());
        return Response.success(room);
    }
}