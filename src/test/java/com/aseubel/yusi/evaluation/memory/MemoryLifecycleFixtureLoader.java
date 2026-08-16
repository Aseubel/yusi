package com.aseubel.yusi.evaluation.memory;

import com.aseubel.yusi.evaluation.EvaluationFixtureRedLineValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.EvaluationCase;
import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.Expected;
import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.MemoryRecord;
import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.Scenario;
import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.Suite;
import static com.aseubel.yusi.evaluation.memory.MemoryLifecycleEvaluationFixture.VectorCandidate;

/** Loads only the fixed, sanitized memory lifecycle replay schema. */
public final class MemoryLifecycleFixtureLoader {

    public static final String DEFAULT_RESOURCE =
            "evaluation/memory-lifecycle-v1-fixtures.json";
    private static final String INVALID_CODE = "FIXTURE_INVALID";
    private static final Pattern CASE_ID = Pattern.compile("EVAL-MEM-001");
    private static final Pattern SCENARIO_ID = Pattern.compile("EVAL-MEM-001-[A-C]");
    private static final Pattern USER_ID = Pattern.compile("fixture-user-[a-z0-9-]+");
    private static final Pattern MEMORY_ID = Pattern.compile("fixture-memory-[a-z0-9-]+");
    private static final Pattern SUMMARY_TOKEN = Pattern.compile("memory-summary-[a-z0-9-]+");
    private static final Set<String> LIFECYCLES = Set.of("ACTIVE", "HIDDEN", "EXPIRED", "MERGED");
    private static final Set<String> REQUIRED_SCENARIOS = Set.of(
            "EVAL-MEM-001-A", "EVAL-MEM-001-B", "EVAL-MEM-001-C");

    private final ObjectMapper objectMapper;

    public MemoryLifecycleFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public Suite load() {
        try (InputStream input = new ClassPathResource(DEFAULT_RESOURCE).getInputStream()) {
            return load(objectMapper.readTree(input));
        } catch (IOException exception) {
            throw invalid();
        }
    }

    public Suite load(JsonNode root) {
        try {
            EvaluationFixtureRedLineValidator.validateTree(root);
            Suite suite = objectMapper.readerFor(Suite.class).readValue(root.toString());
            validateTypedSuite(suite);
            return suite;
        } catch (EvaluationFixtureRedLineValidator.FixtureValidationException exception) {
            throw invalid();
        } catch (FixtureValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private void validateTypedSuite(Suite suite) {
        if (suite == null || suite.schemaVersion() != 1
                || !"memory-lifecycle-v1".equals(suite.suiteId())
                || suite.cases() == null || suite.cases().isEmpty()) {
            throw invalid();
        }
        Set<String> caseIds = new HashSet<>();
        for (EvaluationCase evaluationCase : suite.cases()) {
            if (evaluationCase == null || !matches(CASE_ID, evaluationCase.caseId())
                    || !caseIds.add(evaluationCase.caseId())
                    || evaluationCase.scenarios() == null
                    || !REQUIRED_SCENARIOS.equals(evaluationCase.scenarios().stream()
                    .filter(scenario -> scenario != null)
                    .map(Scenario::scenarioId)
                    .collect(java.util.stream.Collectors.toSet()))) {
                throw invalid();
            }
            Set<String> scenarioIds = new HashSet<>();
            for (Scenario scenario : evaluationCase.scenarios()) {
                if (scenario == null || !scenarioIds.add(scenario.scenarioId())) {
                    throw invalid();
                }
                validateScenario(scenario);
            }
        }
    }

    private void validateScenario(Scenario scenario) {
        if (!matches(SCENARIO_ID, scenario.scenarioId())
                || !matches(USER_ID, scenario.userId())
                || scenario.memories() == null || scenario.memories().isEmpty()
                || scenario.vectorCandidates() == null || scenario.vectorCandidates().isEmpty()
                || !matches(MEMORY_ID, scenario.positiveMemoryKey())
                || scenario.expected() == null) {
            throw invalid();
        }

        Set<String> memoryKeys = new HashSet<>();
        for (MemoryRecord memory : scenario.memories()) {
            if (memory == null || !matches(MEMORY_ID, memory.memoryKey())
                    || !memoryKeys.add(memory.memoryKey())
                    || !matches(USER_ID, memory.ownerUserId())
                    || !matches(SUMMARY_TOKEN, memory.summaryToken())
                    || !LIFECYCLES.contains(memory.lifecycle())) {
                throw invalid();
            }
            if ("MERGED".equals(memory.lifecycle())
                    && (!matches(MEMORY_ID, memory.mergedIntoKey())
                    || memory.mergedIntoKey().equals(memory.memoryKey()))) {
                throw invalid();
            }
        }
        for (MemoryRecord memory : scenario.memories()) {
            if ("MERGED".equals(memory.lifecycle()) && !memoryKeys.contains(memory.mergedIntoKey())) {
                throw invalid();
            }
        }

        for (VectorCandidate candidate : scenario.vectorCandidates()) {
            if (candidate == null || !matches(MEMORY_ID, candidate.memoryKey())
                    || !matches(USER_ID, candidate.ownerUserId())
                    || !matches(SUMMARY_TOKEN, candidate.summaryToken())
                    || !memoryKeys.contains(candidate.memoryKey())) {
                throw invalid();
            }
            MemoryRecord memory = findMemory(scenario, candidate.memoryKey());
            if (!memory.ownerUserId().equals(candidate.ownerUserId())
                    || !memory.summaryToken().equals(candidate.summaryToken())) {
                throw invalid();
            }
        }

        if (!memoryKeys.contains(scenario.positiveMemoryKey())
                || !ownerOf(scenario, scenario.positiveMemoryKey()).equals(scenario.userId())) {
            throw invalid();
        }
        if (scenario.deleteMemoryKey() != null
                && (!matches(MEMORY_ID, scenario.deleteMemoryKey())
                || !memoryKeys.contains(scenario.deleteMemoryKey()))) {
            throw invalid();
        }
        validateExpected(scenario, memoryKeys);
        validateScenarioShape(scenario);
    }

    private void validateExpected(Scenario scenario, Set<String> memoryKeys) {
        Expected expected = scenario.expected();
        if (!hasKeys(expected.availableKeys(), memoryKeys)
                || !hasKeys(expected.matchableKeys(), memoryKeys)
                || !hasKeys(expected.retrievedKeys(), memoryKeys)
                || !hasKeys(expected.restrictedKeys(), memoryKeys)
                || !memoryKeys.contains(expected.retainedUserMemoryKey())
                || !expected.retrievedKeys().contains(scenario.positiveMemoryKey())) {
            throw invalid();
        }
        if (expected.otherUserId() != null && !matches(USER_ID, expected.otherUserId())) {
            throw invalid();
        }
        if (expected.otherUserMemoryKey() != null
                && (!memoryKeys.contains(expected.otherUserMemoryKey())
                || expected.otherUserId() == null
                || !ownerOf(scenario, expected.otherUserMemoryKey()).equals(expected.otherUserId()))) {
            throw invalid();
        }
    }

    private void validateScenarioShape(Scenario scenario) {
        String scenarioId = scenario.scenarioId();
        List<MemoryRecord> memories = scenario.memories();
        long hiddenCount = memories.stream().filter(memory -> "HIDDEN".equals(memory.lifecycle())).count();
        long expiredCount = memories.stream().filter(memory -> "EXPIRED".equals(memory.lifecycle())).count();
        long mergedCount = memories.stream().filter(memory -> "MERGED".equals(memory.lifecycle())).count();
        if (scenarioId.endsWith("-A")) {
            boolean hasChatOnly = memories.stream()
                    .anyMatch(memory -> "ACTIVE".equals(memory.lifecycle()) && !memory.matchAllowed());
            if (memories.stream().noneMatch(memory -> "ACTIVE".equals(memory.lifecycle()))
                    || !hasChatOnly || hiddenCount == 0 || expiredCount == 0 || mergedCount == 0
                    || scenario.deleteMemoryKey() != null || scenario.vectorDeleteFails()) {
                throw invalid();
            }
        } else if (scenarioId.endsWith("-B")) {
            MemoryRecord positive = findMemory(scenario, scenario.positiveMemoryKey());
            boolean hasRestrictedOrNonMatchable = memories.stream()
                    .anyMatch(memory -> !memory.matchAllowed() || !"ACTIVE".equals(memory.lifecycle()));
            if (!"ACTIVE".equals(positive.lifecycle()) || !positive.matchAllowed()
                    || !hasRestrictedOrNonMatchable || hiddenCount == 0 || expiredCount == 0
                    || mergedCount == 0 || scenario.deleteMemoryKey() != null || scenario.vectorDeleteFails()) {
                throw invalid();
            }
        } else if (scenarioId.endsWith("-C")) {
            validateDeleteScenario(scenario);
        } else {
            throw invalid();
        }
    }

    private void validateDeleteScenario(Scenario scenario) {
        Expected expected = scenario.expected();
        if (!scenario.vectorDeleteFails() || scenario.deleteMemoryKey() == null
                || !expected.retainedUserMemoryKey().matches(MEMORY_ID.pattern())
                || expected.retainedUserMemoryKey().equals(scenario.deleteMemoryKey())
                || expected.otherUserId() == null || expected.otherUserMemoryKey() == null) {
            throw invalid();
        }
        MemoryRecord deleteTarget = findMemory(scenario, scenario.deleteMemoryKey());
        MemoryRecord retained = findMemory(scenario, expected.retainedUserMemoryKey());
        MemoryRecord other = findMemory(scenario, expected.otherUserMemoryKey());
        if (!scenario.userId().equals(deleteTarget.ownerUserId())
                || !scenario.userId().equals(retained.ownerUserId())
                || scenario.userId().equals(expected.otherUserId())
                || !expected.otherUserId().equals(other.ownerUserId())) {
            throw invalid();
        }
    }

    private boolean hasKeys(Set<String> keys, Set<String> memoryKeys) {
        return keys != null && !keys.isEmpty() && memoryKeys.containsAll(keys);
    }

    private MemoryRecord findMemory(Scenario scenario, String memoryKey) {
        return scenario.memories().stream()
                .filter(memory -> memory.memoryKey().equals(memoryKey))
                .findFirst()
                .orElseThrow(this::invalid);
    }

    private String ownerOf(Scenario scenario, String memoryKey) {
        return findMemory(scenario, memoryKey).ownerUserId();
    }

    private boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private FixtureValidationException invalid() {
        return new FixtureValidationException(INVALID_CODE);
    }

    public static final class FixtureValidationException extends RuntimeException {
        private final String code;

        public FixtureValidationException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
