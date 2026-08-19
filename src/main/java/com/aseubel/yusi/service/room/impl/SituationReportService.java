package com.aseubel.yusi.service.room.impl;

import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.common.utils.LowSensitivityLogSummary;
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
            SituationScenario scenario = scenarioRepository.findById(room.getScenarioId()).orElse(null);
            if (ObjectUtil.isEmpty(scenario)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Scenario not found: " + room.getScenarioId());
            }

            String scenarioString = scenario.getContentString();
            String userAnswersJson = objectMapper.writeValueAsString(room.getSubmissions());

            CompletableFuture<String> future = new CompletableFuture<>();
            StringBuilder sb = new StringBuilder();

            try {
                com.aseubel.yusi.service.ai.model.ModelRouteContextHolder.set(
                        com.aseubel.yusi.service.ai.model.ModelRouteContext.builder()
                                .scene(PromptKey.LOGIC.getKey())
                                .maskSensitiveData(false)
                                .build());
                situationRoomAgent.analyzeReport(scenarioString, userAnswersJson)
                        .onPartialResponse(sb::append)
                        .onCompleteResponse(res -> future.complete(sb.toString()))
                        .onError(future::completeExceptionally)
                        .start();
            } finally {
                com.aseubel.yusi.service.ai.model.ModelRouteContextHolder.clear();
            }

            String jsonReport = future.get();
            log.info("SituationReport analysis output: scenarioId={}, status=received, outputLengthBucket={}",
                    room.getScenarioId(), LowSensitivityLogSummary.lengthBucket(jsonReport));

            String cleanJson = extractValidJson(jsonReport);

            SituationReport report = objectMapper.readValue(cleanJson, SituationReport.class);
            report.setScenarioId(room.getScenarioId());

            List<SituationReport.PublicSubmission> publicSubmissions = report.extractPublicSubmissions(room);
            report.setPublicSubmissions(publicSubmissions);

            return report;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("SituationReport analysis failed: operation=situation_report_analyze, exceptionType={}",
                    LowSensitivityLogSummary.exceptionType(e));
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "AI分析失败，请稍后重试");
        }
    }

    private String extractValidJson(String raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "AI返回为空");
        }
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

        int jsonStart = trimmed.indexOf("{");
        int jsonEnd = trimmed.lastIndexOf("}");
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            String possibleJson = trimmed.substring(jsonStart, jsonEnd + 1);
            try {
                objectMapper.readTree(possibleJson);
                return possibleJson;
            } catch (Exception e) {
                log.warn("SituationReport JSON candidate invalid: operation=extract_json, category=invalid_json, exceptionType={}",
                        LowSensitivityLogSummary.exceptionType(e));
            }
        }

        if (trimmed.contains("Thinking Process:") || trimmed.contains("```")) {
            log.error("SituationReport model format invalid: operation=extract_json, category=thinking_or_markdown, outputLengthBucket={}",
                    LowSensitivityLogSummary.lengthBucket(trimmed));
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "AI返回格式错误，请稍后重试");
        }

        return trimmed;
    }
}
