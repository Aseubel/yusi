package com.aseubel.yusi.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveLogSourceAuditTest {

    private static final String MAIN_ROOT = "src/main/java";

    private static final Set<String> MODIFIED_LOG_FILES = Set.of(
            "src/main/java/com/aseubel/yusi/common/utils/SpelResolverHelper.java",
            "src/main/java/com/aseubel/yusi/config/ai/PersistentChatMemoryStore.java",
            "src/main/java/com/aseubel/yusi/grpc/McpGrpcServiceImpl.java",
            "src/main/java/com/aseubel/yusi/service/ai/tool/DiarySearchTool.java",
            "src/main/java/com/aseubel/yusi/service/ai/tool/LifeGraphTool.java",
            "src/main/java/com/aseubel/yusi/service/ai/tool/UserPersonaTool.java",
            "src/main/java/com/aseubel/yusi/service/cognition/CognitiveConflictDetector.java",
            "src/main/java/com/aseubel/yusi/service/cognition/MidMemoryFusionService.java",
            "src/main/java/com/aseubel/yusi/service/diary/impl/DiaryServiceImpl.java",
            "src/main/java/com/aseubel/yusi/service/location/impl/UserLocationServiceImpl.java",
            "src/main/java/com/aseubel/yusi/service/match/impl/MatchServiceImpl.java",
            "src/main/java/com/aseubel/yusi/service/memory/MidTermMemorySearchService.java",
            "src/main/java/com/aseubel/yusi/service/plaza/impl/EmotionAnalyzerImpl.java",
            "src/main/java/com/aseubel/yusi/service/room/impl/SituationReportService.java",
            "src/main/java/com/aseubel/yusi/service/user/impl/TokenServiceImpl.java",
            "src/main/java/com/aseubel/yusi/redis/aspect/SpelResolverAspect.java");

    private static final List<DeferredException> DEFERRED_EXCEPTIONS = List.of(
            new DeferredException("GlobalExceptionHandler.java:86",
                    "SSE response already started; global exception policy is deferred"),
            new DeferredException("GlobalExceptionHandler.java:89",
                    "central HTTP exception handler stack policy is deferred"),
            new DeferredException("ModelProxyFactory.java:279",
                    "model retry error classification and sampling policy is deferred"),
            new DeferredException("PromptManager.java:64",
                    "database prompt loading exception policy is deferred"),
            new DeferredException("PromptManager.java:123",
                    "prompt auto-initialization exception policy is deferred"),
            new DeferredException("AdminServiceImpl.java:302",
                    "fixed internal cleanup SQL and exception policy is deferred"),
            new DeferredException("CacheAspect.java:267",
                    "cache key and throwable logging policy is deferred"));

    private static final Pattern LOGGER_START = Pattern.compile(
            "\\b(?:log|logger)\\.(?:trace|debug|info|warn|error)\\s*\\(");
    private static final Pattern DIRECT_PAYLOAD = Pattern.compile(
            "(?i)(?:,\\s*(?:query|keyword|plainContent|profileText|jsonReport|imagesJson|"+
                    "preferredName|customInstructions|description|reason|deviceInfo|payload)\\s*(?:,|\\))|"+
                    "\\b(?:request|user|userA|userB)\\.get(?:Name|UserName)\\s*\\(\\)|,\\s*spelExpression\\s*,|"+
                    "\\bresolvedValue\\s*,)");
    private static final Pattern EXCEPTION_MESSAGE = Pattern.compile(
            "(?i)\\.getMessage\\s*\\(\\)|\\b(?:Throwable|printStackTrace|getStackTrace)\\b");
    private static final Pattern THROWABLE_ARGUMENT = Pattern.compile(
            "(?i),\\s*(?:e|ex|exception|error|cause)\\s*\\)");

    @Test
    void allProductionLoggerProjectionsStayWithinTheLowSensitivityBoundary() throws IOException {
        List<LoggerInvocation> invocations = readLoggerInvocations();
        Set<String> observedDeferred = new LinkedHashSet<>();
        List<String> directPayloadFindings = new ArrayList<>();
        List<String> modifiedMessageStackFindings = new ArrayList<>();

        for (LoggerInvocation invocation : invocations) {
            String location = deferredLocation(invocation);
            String normalizedArguments = safeProjection(invocation.block());
            boolean deferred = location != null;

            if (DIRECT_PAYLOAD.matcher(normalizedArguments).find() && !deferred) {
                directPayloadFindings.add(invocation.location() + ":DIRECT_PAYLOAD");
            }
            if (deferred && isDeferredExceptionBlock(invocation.block())) {
                observedDeferred.add(location);
            }
            if (MODIFIED_LOG_FILES.contains(invocation.file())
                    && (EXCEPTION_MESSAGE.matcher(invocation.block()).find()
                    || THROWABLE_ARGUMENT.matcher(invocation.block()).find())) {
                modifiedMessageStackFindings.add(invocation.location() + ":MESSAGE_OR_STACK");
            }
        }

        assertEquals(List.of(), directPayloadFindings,
                "SECURITY_LOG_DIRECT_PAYLOAD:" + String.join(",", directPayloadFindings));
        assertEquals(List.of(), modifiedMessageStackFindings,
                "SECURITY_LOG_MODIFIED_MESSAGE_STACK:" + String.join(",", modifiedMessageStackFindings));
        assertEquals(DEFERRED_EXCEPTIONS.stream().map(DeferredException::location).collect(Collectors.toCollection(LinkedHashSet::new)),
                observedDeferred, "SECURITY_LOG_DEFERRED_ALLOWLIST_MISMATCH");
        assertFalse(invocations.isEmpty(), "SECURITY_LOG_NO_LOGGERS_SCANNED");
    }

    @Test
    void deferredExceptionAllowlistIsExplicitAndReasoned() {
        assertEquals(7, DEFERRED_EXCEPTIONS.size());
        assertEquals(7, DEFERRED_EXCEPTIONS.stream().map(DeferredException::location).distinct().count());
        assertTrue(DEFERRED_EXCEPTIONS.stream().allMatch(entry -> !entry.reason().isBlank()));
        assertTrue(DEFERRED_EXCEPTIONS.stream().noneMatch(entry -> entry.location().contains("*")
                || entry.location().contains("/")));
    }

    private List<LoggerInvocation> readLoggerInvocations() throws IOException {
        List<LoggerInvocation> invocations = new ArrayList<>();
        try (var paths = Files.walk(Path.of(MAIN_ROOT))) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(path);
                String file = normalize(path.toString());
                for (int index = 0; index < lines.size(); index++) {
                    Matcher matcher = LOGGER_START.matcher(lines.get(index));
                    if (!matcher.find()) {
                        continue;
                    }
                    int end = index;
                    StringBuilder block = new StringBuilder(lines.get(index));
                    while (end < lines.size() - 1 && !lines.get(end).trim().endsWith(");")) {
                        end++;
                        block.append(' ').append(lines.get(end).trim());
                    }
                    invocations.add(new LoggerInvocation(file, end, index + 1, block.toString()));
                    index = end;
                }
            }
        }
        return invocations;
    }

    private String safeProjection(String block) {
        String withoutStrings = block.replaceAll("\\\"(?:\\\\.|[^\\\"])*\\\"", "\\\"\\\"");
        String withoutSafeSummaries = withoutStrings
                .replaceAll("LowSensitivityLogSummary\\.lengthBucket\\s*\\([^)]*\\)", "")
                .replaceAll("LowSensitivityLogSummary\\.exceptionType\\s*\\([^)]*\\)", "")
                .replaceAll("StrUtil\\.isNotBlank\\s*\\([^)]*\\)", "")
                .replaceAll("StrUtil\\.isBlank\\s*\\([^)]*\\)", "");
        return withoutSafeSummaries;
    }

    private boolean isDeferredExceptionBlock(String block) {
        return EXCEPTION_MESSAGE.matcher(block).find() || THROWABLE_ARGUMENT.matcher(block).find();
    }

    private String deferredLocation(LoggerInvocation invocation) {
        String fileName = Path.of(invocation.file()).getFileName().toString();
        String location = fileName + ":" + invocation.startLine();
        return DEFERRED_EXCEPTIONS.stream()
                .map(DeferredException::location)
                .filter(location::equals)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        return value.replace('\\', '/');
    }

    private record LoggerInvocation(String file, int endLine, int startLine, String block) {
        private String location() {
            return Path.of(file).getFileName() + ":" + startLine;
        }
    }

    private record DeferredException(String location, String reason) {
    }
}
