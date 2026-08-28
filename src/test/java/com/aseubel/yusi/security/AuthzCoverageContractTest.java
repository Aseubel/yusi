package com.aseubel.yusi.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source contract for the HTTP authentication boundary.
 *
 * <p>This deliberately uses the same mapping scan shape as
 * {@code RateLimitCoverageContractTest}; it is a source inventory, not a
 * substitute for runtime authorization tests.</p>
 */
class AuthzCoverageContractTest {

    static final Path SOURCE_ROOT = Path.of("src/main/java/com/aseubel/yusi/controller");
    static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    private static final Pattern REQUEST_MAPPING_PATTERN = Pattern.compile("@RequestMapping\\(\\\"([^\\\"]+)\\\"\\)");
    private static final Pattern MAPPING_PATTERN = Pattern.compile(
            "@(?<method>Get|Post|Put|Delete|Patch)Mapping(?:\\((?<args>[^\\n]*)\\))?");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+\\w+");
    private static final Pattern OPTIONAL_AUTH_PATTERN = Pattern.compile(
            "@Auth\\s*\\(\\s*required\\s*=\\s*false\\s*\\)");

    @Test
    void locksExactRouteAndAuthenticationBaseline() throws IOException {
        List<Mapping> mappings = scanMappings();

        assertThat(mappings).hasSize(166);
        assertThat(mappings.stream().filter(mapping -> WRITE_METHODS.contains(mapping.httpMethod())).count())
                .isEqualTo(94);
        assertThat(mappings.stream().filter(mapping -> !WRITE_METHODS.contains(mapping.httpMethod())).count())
                .isEqualTo(72);
        assertThat(mappings).noneMatch(mapping -> mapping.endpoint().equals("POST /api/match/run"));

        assertThat(mappings.stream().filter(Mapping::requiredAuth).count()).isEqualTo(154);
        assertThat(mappings.stream().filter(Mapping::hasExplicitAuthContract).count()).isEqualTo(162);
        assertThat(mappings.stream().filter(mapping -> !mapping.hasExplicitAuthContract())
                .map(Mapping::endpoint))
                .containsExactlyInAnyOrder(
                        "GET /api/geo/search",
                        "GET /api/geo/reverse",
                        "GET /api/health",
                        "POST /api/suggestions");
    }

    @Test
    void locksExactControllerMappingCounts() throws IOException {
        Map<String, Long> actual = scanMappings().stream()
                .collect(Collectors.groupingBy(Mapping::controllerName, LinkedHashMap::new, Collectors.counting()));

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                Map.entry("AdminController", 20L),
                Map.entry("AiController", 12L),
                Map.entry("DeveloperConfigController", 4L),
                Map.entry("DiaryController", 6L),
                Map.entry("GeoController", 2L),
                Map.entry("ImageController", 10L),
                Map.entry("KeyManagementController", 6L),
                Map.entry("LifeGraphController", 16L),
                Map.entry("MatchController", 8L),
                Map.entry("MemoryCenterController", 9L),
                Map.entry("ModelManagementController", 12L),
                Map.entry("NotificationController", 6L),
                Map.entry("PingController", 1L),
                Map.entry("PromptController", 6L),
                Map.entry("RoomChatController", 3L),
                Map.entry("SituationRoomController", 16L),
                Map.entry("SoulChatController", 4L),
                Map.entry("SoulPlazaController", 10L),
                Map.entry("StatsController", 1L),
                Map.entry("SuggestionController", 2L),
                Map.entry("UserController", 8L),
                Map.entry("UserLocationController", 4L)));
    }

    static List<Mapping> scanMappings() throws IOException {
        List<Mapping> mappings = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.list(SOURCE_ROOT)) {
            for (Path file : files
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                List<String> lines = Files.readAllLines(file);
                String classPrefix = classPrefix(lines);
                int classLine = classDeclarationLine(lines);
                boolean classAuth = hasAuthAnnotation(lines, 0, classLine + 1);
                List<Integer> mappingLines = mappingLineIndexes(lines);
                int previousMappingLine = classLine;
                for (int lineIndex : mappingLines) {
                    Matcher matcher = MAPPING_PATTERN.matcher(lines.get(lineIndex));
                    if (!matcher.find()) {
                        continue;
                    }
                    String methodPath = firstQuoted(matcher.group("args"));
                    boolean methodAuth = hasAuthAnnotation(lines, previousMappingLine + 1, lineIndex + 1)
                            || hasAuthAnnotationAfterMapping(lines, lineIndex + 1);
                    boolean optionalMethodAuth = hasOptionalAuthAnnotation(
                            lines, previousMappingLine + 1, lineIndex + 1)
                            || hasOptionalAuthAnnotationAfterMapping(lines, lineIndex + 1);
                    boolean explicitAuth = classAuth || methodAuth;
                    boolean requiredAuth = explicitAuth && !optionalMethodAuth;
                    mappings.add(new Mapping(
                            file.getFileName().toString(),
                            matcher.group("method").toUpperCase(),
                            joinPath(classPrefix, methodPath),
                            file,
                            lineIndex + 1,
                            requiredAuth,
                            explicitAuth));
                    previousMappingLine = lineIndex;
                }
            }
        }
        return mappings;
    }

    private static List<Integer> mappingLineIndexes(List<String> lines) {
        List<Integer> indexes = new java.util.ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (MAPPING_PATTERN.matcher(lines.get(index)).find()) {
                indexes.add(index);
            }
        }
        return indexes;
    }

    private static boolean hasAuthAnnotation(List<String> lines, int startInclusive, int endExclusive) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(lines.size(), endExclusive);
        for (int index = start; index < end; index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("@Auth")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOptionalAuthAnnotation(List<String> lines, int startInclusive, int endExclusive) {
        int start = Math.max(0, startInclusive);
        int end = Math.min(lines.size(), endExclusive);
        for (int index = start; index < end; index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (OPTIONAL_AUTH_PATTERN.matcher(trimmed).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAuthAnnotationAfterMapping(List<String> lines, int startInclusive) {
        int end = Math.min(lines.size(), startInclusive + 8);
        for (int index = Math.max(0, startInclusive); index < end; index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("public ") || trimmed.startsWith("protected ")
                    || trimmed.startsWith("private ")) {
                return false;
            }
            if (trimmed.startsWith("@Auth")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOptionalAuthAnnotationAfterMapping(List<String> lines, int startInclusive) {
        int end = Math.min(lines.size(), startInclusive + 8);
        for (int index = Math.max(0, startInclusive); index < end; index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("public ") || trimmed.startsWith("protected ")
                    || trimmed.startsWith("private ")) {
                return false;
            }
            if (OPTIONAL_AUTH_PATTERN.matcher(trimmed).find()) {
                return true;
            }
        }
        return false;
    }

    private static int classDeclarationLine(List<String> lines) {
        for (int index = 0; index < lines.size(); index++) {
            if (CLASS_PATTERN.matcher(lines.get(index)).find()) {
                return index;
            }
        }
        throw new IllegalArgumentException("Controller class declaration not found");
    }

    private static String classPrefix(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = REQUEST_MAPPING_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
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
        return left.endsWith("/")
                ? left.substring(0, left.length() - 1) + "/" + right.replaceFirst("^/", "")
                : left + (right.startsWith("/") ? right : "/" + right);
    }

    private static String firstQuoted(String value) {
        if (value == null) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\\"([^\\\"]*)\\\"").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    record Mapping(
            String sourceFileName,
            String httpMethod,
            String path,
            Path sourceFile,
            int lineNumber,
            boolean requiredAuth,
            boolean hasExplicitAuthContract) {

        String controllerName() {
            return sourceFileName.replaceFirst("\\.java$", "");
        }

        String endpoint() {
            return httpMethod + " " + path;
        }
    }
}
