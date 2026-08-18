package com.aseubel.yusi.evaluation.chat;

import com.aseubel.yusi.evaluation.OfflineEvaluationReportWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Report envelope for the deterministic chat quality suite. */
public final class ChatQualityEvaluationReport {

    public static final String SUITE_ID = "chat-quality-v1";

    private ChatQualityEvaluationReport() {
    }

    public static void write(Path path, List<OfflineEvaluationReportWriter.CaseResult> cases)
            throws IOException {
        OfflineEvaluationReportWriter.write(path, SUITE_ID, cases);
    }
}
