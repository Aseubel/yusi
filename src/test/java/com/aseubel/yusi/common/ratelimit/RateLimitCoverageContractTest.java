package com.aseubel.yusi.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitCoverageContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/aseubel/yusi/controller");
    private static final Set<String> SENTINELS = Set.of(
            "fixture-user-rate",
            "fixture-query-rate",
            "fixture-content-rate",
            "fixture-token-rate",
            "fixture-object-key-rate");

    private static final List<String> EXPECTED_WRITE_ENDPOINTS = List.of(
            "POST /api/admin/users/{userId}/permission",
            "POST /api/admin/scenarios/{scenarioId}/audit",
            "POST /api/admin/suggestions/{suggestionId}/reply",
            "POST /api/admin/suggestions/{suggestionId}/status",
            "POST /api/admin/announcements",
            "POST /api/admin/embeddings/full-sync",
            "POST /api/admin/users/{userId}/deregister",
            "PUT /api/admin/web-access-policy",
            "POST /api/ai/chat/stream",
            "POST /api/ai/chat/cancel",
            "PUT /api/ai/persona-config",
            "POST /api/ai/cognitive-conflicts/{id}/resolve",
            "POST /api/ai/memory-fusion/run",
            "POST /api/ai/chat/inject-greeting",
            "POST /api/developer/config/api-key",
            "PUT /api/developer/config/api-key/scopes",
            "DELETE /api/developer/config/api-key",
            "POST /api/diary",
            "PUT /api/diary",
            "POST /api/diary/chat",
            "POST /api/image/upload",
            "POST /api/image/upload/batch",
            "POST /api/image/chunk/upload",
            "POST /api/image/chunk/merge",
            "POST /api/image/urls",
            "DELETE /api/image",
            "DELETE /api/image/batch",
            "POST /api/key/settings",
            "POST /api/key/reencrypt-diaries",
            "POST /api/key/recovery/send-code",
            "POST /api/key/recovery",
            "POST /api/lifegraph/merge-suggestions/{judgmentId}/accept",
            "POST /api/lifegraph/merge-suggestions/{judgmentId}/reject",
            "POST /api/lifegraph/entities",
            "PUT /api/lifegraph/entities/{id}",
            "DELETE /api/lifegraph/entities/{id}",
            "POST /api/lifegraph/relations",
            "PUT /api/lifegraph/relations/{id}",
            "DELETE /api/lifegraph/relations/{id}",
            "POST /api/match/settings",
            "POST /api/match/{matchId}/action",
            "POST /api/match/{matchId}/feedback",
            "POST /api/match/{matchId}/end",
            "POST /api/match/{matchId}/report",
            "POST /api/match/{matchId}/block",
            "PATCH /api/memory/center/{id}",
            "DELETE /api/memory/center/{id}",
            "PATCH /api/memory/persona",
            "DELETE /api/memory/persona",
            "PATCH /api/memory/life-graph/{id}",
            "DELETE /api/memory/life-graph/{id}",
            "POST /api/model/states/{modelId}/reset",
            "POST /api/model/states/reset",
            "PUT /api/model/console",
            "POST /api/model/routes/preview",
            "POST /api/notifications/{notificationId}/read",
            "POST /api/notifications/read-all",
            "DELETE /api/notifications/{notificationId}",
            "POST /api/prompt/save",
            "PUT /api/prompt/{id}",
            "POST /api/prompt/{id}/activate",
            "DELETE /api/prompt/{id}",
            "POST /api/room-chat/send",
            "POST /api/room/create",
            "POST /api/room/join",
            "POST /api/room/start",
            "POST /api/room/scenarios/submit",
            "PUT /api/room/scenarios/{id}",
            "DELETE /api/room/scenarios/{id}",
            "POST /api/room/scenarios/{id}/resubmit",
            "POST /api/room/cancel",
            "POST /api/room/vote-cancel",
            "POST /api/room/submit",
            "POST /api/soul-chat/send",
            "POST /api/soul-chat/read",
            "POST /api/plaza/submit",
            "PUT /api/plaza/{cardId}",
            "DELETE /api/plaza/{cardId}",
            "POST /api/plaza/{cardId}/resonate",
            "POST /api/plaza/signal",
            "POST /api/plaza/signals/{signalId}/read",
            "POST /api/suggestions",
            "POST /api/user/register",
            "POST /api/user/register/send-code",
            "POST /api/user/login",
            "POST /api/user/refresh",
            "POST /api/user/forgot-password/send-code",
            "POST /api/user/forgot-password/reset",
            "POST /api/user/update",
            "POST /api/user/logout",
            "POST /api/location",
            "PUT /api/location",
            "DELETE /api/location/{locationId}");

    private static final List<RateLimitSpec> BASELINE_SPECS = List.of(
            spec("GET /api/geo/search", "geo-search", 60, 60, "IP"),
            spec("GET /api/geo/reverse", "geo-reverse", 60, 120, "IP"),
            spec("GET /api/stats/platform", "platform-stats", 60, 30, "IP"),
            spec("GET /api/plaza/feed", "plaza-feed", 60, 60, "IP"),
            spec("GET /api/image/check", "image-upload-check", 60, 60, "USER"),
            spec("GET /api/image/chunk/progress", "image-chunk-progress", 60, 120, "USER"),
            spec("POST /api/ai/chat/stream", "chatStream", 60, 20, "USER"),
            spec("POST /api/user/register", "user-register", 60, 10, "IP"),
            spec("POST /api/user/register/send-code", "register-code", 60, 3, "IP"),
            spec("POST /api/user/login", "login", 60, 10, "IP"),
            spec("POST /api/user/refresh", "refresh", 60, 30, "IP"),
            spec("POST /api/user/forgot-password/send-code", "forgot-password-code", 60, 3, "IP"),
            spec("POST /api/user/forgot-password/reset", "forgot-password-reset", 60, 10, "IP"),
            spec("POST /api/key/recovery/send-code", "key-recovery-code", 60, 3, "IP"),
            spec("POST /api/key/recovery", "key-recovery", 60, 10, "IP"),
            spec("POST /api/suggestions", "suggestion-create", 60, 5, "IP"),
            spec("POST /api/room-chat/send", "room-chat-send", 60, 60, "USER"),
            spec("POST /api/soul-chat/send", "soul-chat-send", 60, 60, "USER"),
            spec("POST /api/image/upload", "image-upload", 60, 20, "USER"),
            spec("POST /api/image/upload/batch", "image-upload-batch", 60, 5, "USER"),
            spec("POST /api/image/chunk/upload", "image-chunk-upload", 60, 120, "USER"),
            spec("POST /api/image/chunk/merge", "image-chunk-merge", 60, 20, "USER"));

    private static final List<RateLimitSpec> REQUIRED_ADDITIONAL_SPECS = List.of(
            spec("POST /api/ai/memory-fusion/run", "memory-fusion-run", 600, 2, "USER"),
            spec("POST /api/room/submit", "room-submit", 600, 3, "USER"),
            spec("POST /api/admin/embeddings/full-sync", "admin-embeddings-full-sync", 3600, 1, "USER"),
            spec("POST /api/image/urls", "image-urls", 60, 60, "USER"),
            spec("DELETE /api/image", "image-delete", 60, 30, "USER"),
            spec("DELETE /api/image/batch", "image-delete-batch", 60, 5, "USER"),
            spec("POST /api/key/reencrypt-diaries", "key-reencrypt-diaries", 600, 2, "USER"));

    @Test
    void coversExactlyNinetyThreeRealWriteMappingsAndExcludesCommentedMapping() throws IOException {
        Set<String> actual = scanMappings().stream()
                .filter(mapping -> WRITE_METHODS.contains(mapping.httpMethod()))
                .map(Mapping::endpoint)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(actual).containsExactlyInAnyOrderElementsOf(EXPECTED_WRITE_ENDPOINTS);
        assertThat(actual).hasSize(93);
        assertThat(actual).doesNotContain("POST /api/match/run");
    }

    @Test
    void preservesAllTwentyTwoApprovedBaselineRateLimiterSpecifications() throws IOException {
        List<RateLimitSpec> actual = scanRateLimitSpecs();

        assertThat(actual).containsAll(BASELINE_SPECS);
        assertThat(BASELINE_SPECS).hasSize(22);
    }

    @Test
    void everyRealWriteMappingHasAnExplicitRateLimiterContract() throws IOException {
        List<Mapping> mappings = scanMappings();

        List<String> missing = new ArrayList<>();
        for (Mapping mapping : mappings) {
            if (WRITE_METHODS.contains(mapping.httpMethod()) && !hasRateLimiterNear(mapping)) {
                missing.add(mapping.endpoint());
            }
        }

        assertThat(missing).as("write endpoints without an explicit limiter").isEmpty();
    }

    @Test
    void highRiskMissingManifestUsesTheApprovedInitialValues() throws IOException {
        List<RateLimitSpec> actual = scanRateLimitSpecs();

        assertThat(actual).containsAll(REQUIRED_ADDITIONAL_SPECS);
    }

    @Test
    void rateLimitSourceDoesNotContainTheFiveSensitiveFixtureSentinels() throws IOException {
        String source = readControllerSources();

        assertThat(SENTINELS).allSatisfy(sentinel -> assertThat(source).doesNotContain(sentinel));
    }

    private boolean hasRateLimiterNear(Mapping mapping) throws IOException {
        List<String> lines = Files.readAllLines(mapping.sourceFile());
        int start = Math.max(0, mapping.lineNumber() - 4);
        int end = Math.min(lines.size(), mapping.lineNumber() + 3);
        for (int index = start; index < end; index++) {
            if (lines.get(index).contains("@RateLimiter")) {
                return true;
            }
        }
        return false;
    }

    private List<RateLimitSpec> scanRateLimitSpecs() throws IOException {
        List<RateLimitSpec> specs = new ArrayList<>();
        for (Mapping mapping : scanMappings()) {
            List<String> lines = Files.readAllLines(mapping.sourceFile());
            int start = Math.max(0, mapping.lineNumber() - 4);
            int end = Math.min(lines.size(), mapping.lineNumber() + 3);
            for (int index = start; index < end; index++) {
                String line = lines.get(index);
                if (line.contains("@RateLimiter")) {
                    specs.add(parseRateLimitSpec(mapping.endpoint(), line));
                    break;
                }
            }
        }
        return specs;
    }

    private RateLimitSpec parseRateLimitSpec(String endpoint, String line) {
        return new RateLimitSpec(endpoint,
                quotedValue(line, "key"),
                integerValue(line, "time", 60),
                integerValue(line, "count", 100),
                enumValue(line, "limitType", "DEFAULT"));
    }

    private List<Mapping> scanMappings() throws IOException {
        List<Mapping> mappings = new ArrayList<>();
        try (Stream<Path> files = Files.list(SOURCE_ROOT)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                List<String> lines = Files.readAllLines(file);
                String classPrefix = classPrefix(lines);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    String trimmed = line.trim();
                    if (trimmed.startsWith("//")) {
                        continue;
                    }
                    Matcher matcher = MAPPING_PATTERN.matcher(line);
                    if (!matcher.find()) {
                        continue;
                    }
                    String methodPath = firstQuoted(matcher.group("args"));
                    mappings.add(new Mapping(
                            matcher.group("method").toUpperCase(),
                            joinPath(classPrefix, methodPath),
                            file,
                            index + 1));
                }
            }
        }
        return mappings;
    }

    private String classPrefix(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = REQUEST_MAPPING_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private String readControllerSources() throws IOException {
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.list(SOURCE_ROOT)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                source.append(Files.readString(file));
            }
        }
        return source.toString();
    }

    private static String joinPath(String prefix, String path) {
        String left = prefix == null ? "" : prefix.trim();
        String right = path == null ? "" : path.trim();
        if (left.isEmpty()) {
            return right.isEmpty() ? "/" : right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left.endsWith("/") ? left.substring(0, left.length() - 1) + "/" + right.replaceFirst("^/", "")
                : left + (right.startsWith("/") ? right : "/" + right);
    }

    private static String firstQuoted(String value) {
        if (value == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String quotedValue(String line, String name) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*\\\"([^\\\"]+)\\\"").matcher(line);
        return matcher.find() ? matcher.group(1) : "rate_limit:";
    }

    private static int integerValue(String line, String name, int defaultValue) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*(\\d+)").matcher(line);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    private static String enumValue(String line, String name, String defaultValue) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*LimitType\\.(\\w+)").matcher(line);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private static RateLimitSpec spec(String endpoint, String key, int time, int count, String limitType) {
        return new RateLimitSpec(endpoint, key, time, count, limitType);
    }

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Pattern REQUEST_MAPPING_PATTERN = Pattern.compile("@RequestMapping\\(\\\"([^\\\"]+)\\\"\\)");
    private static final Pattern MAPPING_PATTERN = Pattern.compile(
            "@(?<method>Get|Post|Put|Delete|Patch)Mapping(?:\\((?<args>[^\\n]*)\\))?");

    private record Mapping(String httpMethod, String path, Path sourceFile, int lineNumber) {
        String endpoint() {
            return httpMethod + " " + path;
        }
    }

    private record RateLimitSpec(String endpoint, String key, int time, int count, String limitType) {
    }
}
