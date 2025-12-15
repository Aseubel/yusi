package com.aseubel.yusi.controller;

import com.aseubel.yusi.common.Response;
import com.aseubel.yusi.pojo.dto.situation.CreateRoomRequest;
import com.aseubel.yusi.pojo.dto.situation.JoinRoomRequest;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.dto.situation.StartRoomRequest;
import com.aseubel.yusi.pojo.dto.situation.SubmitNarrativeRequest;
import com.aseubel.yusi.service.SituationRoomService;
import com.aseubel.yusi.situation.SituationRoom;
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
        SituationRoom room = situationRoomService.createRoom(request.getOwnerId(), Math.max(2, Math.min(8, request.getMaxMembers())));
        return Response.success(room);
    }

    @PostMapping("/join")
    public Response<SituationRoom> join(@RequestBody JoinRoomRequest request) {
        SituationRoom room = situationRoomService.joinRoom(request.getCode(), request.getUserId());
        return Response.success(room);
    }

    @PostMapping("/start")
    public Response<SituationRoom> start(@RequestBody StartRoomRequest request) {
        SituationRoom room = situationRoomService.startRoom(request.getCode(), request.getScenarioId(), request.getOwnerId());
        return Response.success(room);
    }

    @PostMapping("/submit")
    public Response<SituationRoom> submit(@RequestBody SubmitNarrativeRequest request) {
        SituationRoom room = situationRoomService.submit(request.getCode(), request.getUserId(), request.getNarrative());
        return Response.success(room);
    }

    @GetMapping("/report/{code}")
    public Response<SituationReport> report(@PathVariable("code") String code) {
        SituationReport report = situationRoomService.getReport(code);
        return Response.success(report);
    }
}