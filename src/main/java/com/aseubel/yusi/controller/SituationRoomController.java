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

import java.util.List;

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

    @GetMapping("/scenarios/my")
    public Response<List<SituationScenario>> getMyScenarios() {
        return Response.success(situationRoomService.getMyScenarios(UserContext.getUserId()));
    }

    @PutMapping("/scenarios/{id}")
    public Response<SituationScenario> updateScenario(@PathVariable("id") String scenarioId, @RequestBody SubmitScenarioRequest request) {
        return Response.success(situationRoomService.updateScenario(UserContext.getUserId(), scenarioId, request.getTitle(), request.getDescription()));
    }

    @DeleteMapping("/scenarios/{id}")
    public Response<?> deleteScenario(@PathVariable("id") String scenarioId) {
        situationRoomService.deleteScenario(UserContext.getUserId(), scenarioId);
        return Response.success();
    }

    @PostMapping("/scenarios/{id}/resubmit")
    public Response<SituationScenario> resubmitScenario(@PathVariable("id") String scenarioId) {
        return Response.success(situationRoomService.resubmitScenario(UserContext.getUserId(), scenarioId));
    }

    @PostMapping("/scenarios/review")
    public Response<SituationScenario> reviewScenario(@RequestBody ReviewScenarioRequest request) {
        return Response.success(situationRoomService.reviewScenario(UserContext.getUserId(), request.getScenarioId(), request.getStatus(), request.getRejectReason()));
    }

    @GetMapping("/scenarios")
    public Response<List<SituationScenario>> getScenarios() {
        return Response.success(situationRoomService.getScenarios());
    }

    @GetMapping("/scenarios/status")
    public Response<List<SituationScenario>> getScenariosByStatus(@RequestParam(value = "status") Integer status) {
        return Response.success(situationRoomService.getScenariosByStatus(UserContext.getUserId(), status));
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
    public Response<List<SituationRoom>> getHistory() {
        return Response.success(situationRoomService.getHistory(UserContext.getUserId()));
    }

    @GetMapping("/report/{code}")
    public Response<SituationReport> report(@PathVariable("code") String code) {
        SituationReport report = situationRoomService.getReport(code);
        return Response.success(report);
    }

    @GetMapping("/{code}")
    public Response<SituationRoomDetailResponse> getRoom(@PathVariable("code") String code) {
        SituationRoomDetailResponse room = situationRoomService.getRoomDetailResponse(code, UserContext.getUserId());
        return Response.success(room);
    }
}