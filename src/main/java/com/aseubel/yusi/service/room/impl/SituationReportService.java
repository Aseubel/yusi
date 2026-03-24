package com.aseubel.yusi.service.room.impl;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.pojo.dto.situation.SituationReport;
import com.aseubel.yusi.pojo.entity.SituationRoom;
import com.aseubel.yusi.pojo.entity.SituationScenario;
import com.aseubel.yusi.repository.SituationScenarioRepository;
import com.aseubel.yusi.service.room.SituationRoomAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SituationReportService {

    private final SituationRoomAgent situationRoomAgent;
    private final SituationScenarioRepository scenarioRepository;
    private final ObjectMapper objectMapper;

    public SituationReport analyze(SituationRoom room) {
        try {
            // 准备数据
            SituationScenario scenario = scenarioRepository.findById(room.getScenarioId()).orElse(null);
            if (ObjectUtil.isEmpty(scenario)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Scenario not found: " + room.getScenarioId());
            }

            String scenarioString = scenario.getContentString();
            String userAnswersJson = objectMapper.writeValueAsString(room.getSubmissions());

            // 调用 AI
            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder sb = new StringBuilder();
            situationRoomAgent.analyzeReport(scenarioString, userAnswersJson)
                    .onPartialResponse(sb::append)
                    .onCompleteResponse(res -> future.complete(sb.toString()))
                    .onError(future::completeExceptionally)
                    .start();

            String jsonReport = future.get();
            log.info("AI Analysis Result: {}", jsonReport);

            // 清理可能存在的 markdown 标记
            String cleanJson = cleanJsonMarkup(jsonReport);

            // 解析结果
            SituationReport report = objectMapper.readValue(cleanJson, SituationReport.class);
            report.setScenarioId(room.getScenarioId());

            // 过滤出允许公开的回答
            List<SituationReport.PublicSubmission> publicSubmissions = report.extractPublicSubmissions(room);
            report.setPublicSubmissions(publicSubmissions);

            return report;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI Analysis failed", e);
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "AI分析失败，请稍后重试");
        }
    }

    private String cleanJsonMarkup(String raw) {
        if (raw == null) return raw;
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            int endIndex = trimmed.lastIndexOf("```");
            if (endIndex > 6) {
                return trimmed.substring(6, endIndex).trim();
            }
        } else if (trimmed.startsWith("```")) {
            int endIndex = trimmed.lastIndexOf("```");
            if (endIndex > 3) {
                return trimmed.substring(3, endIndex).trim();
            }
        }
        return raw;
    }
}