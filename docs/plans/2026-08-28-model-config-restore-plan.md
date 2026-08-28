# 模型配置恢复（出厂默认 + 历史回滚）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为模型治理控制台增加"恢复出厂默认"与"回滚到历史版本"两个整份配置恢复能力，目标配置先载入草稿预览、确认后由服务端按目标快照发布。

**Architecture:** 复用 `ModelConfigCenter` 现有的 校验→版本+1→`model_runtime_config` 快照落库→`model_config_change_log` 审计→Redis 发布→本地生效 链路，新增 `restoreCanonical` 发布路径（跳过 `validateStableModelIds`，审计 action 分别记 `RESTORE_FACTORY` / `ROLLBACK`）。历史快照来源为 `model_config_change_log.after_json`（脱敏 JSON，apikey=`******`），发布时经 `mergeSecrets` 按 modelId 从当前配置回填密钥；恢复目标中当前已不存在的模型密钥为空，前后端均需明确提示。出厂默认来源为 YAML 绑定的 `bootstrapProperties`。

**Tech Stack:** Spring Boot 3 (Java 21, JPA, Redisson) / React + TS + Vite (pnpm) / i18next

## Global Constraints

- 历史快照是脱敏 JSON（apikey 为 `******`），绝不能把掩码当作真实密钥落库——发布前必须经 `mergeSecrets` 回填。
- 恢复路径必须跳过 `validateStableModelIds`（回滚天然可能移除后来新增的模型），但 `validate()` 其余全部校验必须保留。
- 审计 action 分开：出厂恢复记 `RESTORE_FACTORY`，历史回滚记 `ROLLBACK`；不得记成 `UPDATE_CONFIG`。
- 恢复目标内容一律由服务端读取（change_log 快照 / bootstrap），不信任前端 draft。
- 版本乐观锁：`expectedVersion` 不匹配当前版本时抛 `CONFIG_VERSION_CONFLICT`。
- 前端恢复目标仅作为草稿预览，真正发布走 `POST /model/config/restore`。
- 每个任务完成后运行 `mvn -q test-compile`（后端）或 `pnpm -C frontend build`（前端）验证编译。
- 提交信息用英文 conventional commits（feat:/test:/docs:），参考仓库既有风格。

---

### Task 1: ModelConfigCenter 恢复内核（版本列表 / 快照读取 / 出厂默认 / restoreCanonical）

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java`
- Modify: `src/main/java/com/aseubel/yusi/repository/ModelConfigChangeLogRepository.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigVersionInfo.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/model/ModelConfigCenterRestoreTest.java`

**Interfaces:**
- Consumes: `ModelConfigChangeLogRepository`（JPA）、`ModelRoutingProperties`、`mergeSecrets/validate/saveRuntimeSnapshot/publishRuntimeConfig/applyLocal/toAuditJson`（均为 ModelConfigCenter 现有私有方法）
- Produces:
  - `List<ModelConfigVersionInfo> listRestoreVersions()` — 按 version 去重倒序
  - `ModelRoutingProperties getRestoreSnapshot(long version)` — 找不到抛 `BusinessException(PARAM_ERROR)`
  - `ModelRoutingProperties getFactoryDefaultConfig()` — bootstrap 副本，version 置为当前版本
  - `ModelRoutingProperties restoreCanonical(ModelRoutingProperties target, long expectedVersion, String operatorId, boolean factory)` — factory=true 记 `RESTORE_FACTORY`，否则记 `ROLLBACK`
  - DTO `ModelConfigVersionInfo{changeId, version, operatorId, action, createdAt}`

- [ ] **Step 1: 写失败测试 `ModelConfigCenterRestoreTest`**

```java
package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.common.exception.ErrorCode;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import com.aseubel.yusi.repository.ModelConfigChangeLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigCenterRestoreTest {

    @Test
    void restoreFactoryRecordsRestoreFactoryActionAndBumpsVersion() {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(3L);
        ModelConfigCenter center = centerWith(bootstrap, List.of());

        ModelRoutingProperties restored = center.restoreCanonical(
                center.getFactoryDefaultConfig(), 3L, "admin-1", true);

        assertThat(restored.getVersion()).isEqualTo(4L);
        ArgumentCaptor<ModelConfigChangeLog> logs = ArgumentCaptor.forClass(ModelConfigChangeLog.class);
        verify(changeLogRepository).save(logs.capture());
        assertThat(logs.getValue().getAction()).isEqualTo("RESTORE_FACTORY");
        assertThat(logs.getValue().getSuccess()).isTrue();
    }

    @Test
    void restoreVersionRecordsRollbackAndAllowsRemovingNewlyAddedModel() throws Exception {
        // 当前配置有 qwen + extra；历史快照只有 qwen（无 extra）。普通更新会触发
        // validateStableModelIds 拦截，恢复路径必须放行。
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(5L);
        ModelRoutingProperties historical = validV2Config();
        historical.setVersion(2L);
        ModelConfigCenter center = centerWith(bootstrap, List.of(
                entry("change-2", 2L, new ObjectMapper().writeValueAsString(historical))));

        ModelRoutingProperties restored = center.restoreCanonical(
                center.getRestoreSnapshot(2L), 5L, "admin-1", false);

        assertThat(restored.getVersion()).isEqualTo(6L);
        assertThat(restored.getModels()).extracting(
                        ModelRoutingProperties.ModelDefinition::getId)
                .containsExactly("qwen");
        ArgumentCaptor<ModelConfigChangeLog> logs = ArgumentCaptor.forClass(ModelConfigChangeLog.class);
        verify(changeLogRepository).save(logs.capture());
        assertThat(logs.getValue().getAction()).isEqualTo("ROLLBACK");
    }

    @Test
    void restoreBackfillsApiKeyFromCurrentConfigByModelId() throws Exception {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(5L);
        ModelRoutingProperties historical = validV2Config(); // apikey 在 after_json 中为 ******
        historical.setVersion(2L);
        String redactedJson = new ObjectMapper().writeValueAsString(historical)
                .replace("\"secret\"", "\"******\"");
        ModelConfigCenter center = centerWith(bootstrap, List.of(
                entry("change-2", 2L, redactedJson)));

        ModelRoutingProperties restored = center.restoreCanonical(
                center.getRestoreSnapshot(2L), 5L, "admin-1", false);

        assertThat(restored.getModels().getFirst().getApikey()).isEqualTo("secret");
    }

    @Test
    void restoreRejectsStaleExpectedVersion() {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(3L);
        ModelConfigCenter center = centerWith(bootstrap, List.of());

        assertThatThrownBy(() -> center.restoreCanonical(
                center.getFactoryDefaultConfig(), 2L, "admin-1", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFIG_VERSION_CONFLICT));
    }

    @Test
    void getRestoreSnapshotRejectsUnknownVersion() {
        ModelConfigCenter center = centerWith(validV2Config(), List.of());

        assertThatThrownBy(() -> center.getRestoreSnapshot(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("42");
    }

    @Test
    void listRestoreVersionsDeduplicatesByVersionDescending() throws Exception {
        ModelRoutingProperties historical = validV2Config();
        historical.setVersion(2L);
        String json = new ObjectMapper().writeValueAsString(historical);
        ModelConfigCenter center = centerWith(validV2Config(), List.of(
                entry("change-new", 2L, json),
                entry("change-old", 2L, json),
                entry("change-one", 1L, json.replace("\"version\":2", "\"version\":1"))));

        List<ModelConfigVersionInfo> versions = center.listRestoreVersions();

        assertThat(versions).extracting(ModelConfigVersionInfo::getVersion)
                .containsExactly(2L, 1L);
        assertThat(versions.getFirst().getChangeId()).isEqualTo("change-new");
    }

    @Test
    void factoryDefaultCarriesCurrentVersionAndNoSecret() {
        ModelRoutingProperties bootstrap = validV2Config();
        bootstrap.setVersion(9L);
        ModelConfigCenter center = centerWith(bootstrap, List.of());

        ModelRoutingProperties factory = center.getFactoryDefaultConfig();

        assertThat(factory.getVersion()).isEqualTo(9L);
        assertThat(factory.getModels().getFirst().getApikey()).isEqualTo("secret");
    }

    private ModelConfigChangeLogRepository changeLogRepository;

    private ModelConfigCenter centerWith(ModelRoutingProperties bootstrap,
            List<ModelConfigChangeLog> history) {
        ModelRuntimeConfigRepositoryHolder holder = new ModelRuntimeConfigRepositoryHolder();
        changeLogRepository = mock(ModelConfigChangeLogRepository.class);
        when(changeLogRepository.findTop500BySuccessTrueOrderByCreatedAtDesc()).thenReturn(history);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBucket<String> bucket = mock(RBucket.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.<String>getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        when(bucket.get()).thenReturn(null);
        ModelConfigCenter center = new ModelConfigCenter(bootstrap, redissonClient,
                new ObjectMapper(), null, holder.empty(), changeLogRepository);
        center.init();
        return center;
    }

    /** 占位：ModelRuntimeConfigRepository 为空时走 currentConfigForWrite 的本地分支。 */
    private static final class ModelRuntimeConfigRepositoryHolder {
        com.aseubel.yusi.repository.ModelRuntimeConfigRepository empty() {
            return null;
        }
    }

    private ModelConfigChangeLog entry(String changeId, long version, String afterJson) {
        return ModelConfigChangeLog.builder()
                .changeId(changeId)
                .action("UPDATE_CONFIG")
                .afterJson(afterJson)
                .success(true)
                .build();
    }

    private ModelRoutingProperties validV2Config() {
        ModelRoutingProperties config = new ModelRoutingProperties();
        config.setSchemaVersion(2);
        config.setModels(List.of(model("qwen")));

        ModelTierDefinition tier = new ModelTierDefinition();
        tier.setMembers(List.of("qwen"));
        tier.setStrategy(ModelSelectionStrategyType.FAIL_OVER);
        config.setTiers(new LinkedHashMap<>(Map.of("balanced", tier)));

        RoutePolicyDefinition route = new RoutePolicyDefinition();
        route.setId("chat");
        route.setScene("chat");
        route.setPrimaryTier("balanced");
        route.setEnabled(true);
        config.setRoutes(List.of(route));
        return config;
    }

    private ModelRoutingProperties.ModelDefinition model(String id) {
        ModelRoutingProperties.ModelDefinition model = new ModelRoutingProperties.ModelDefinition();
        model.setId(id);
        model.setProvider("openai-compatible");
        model.setProtocol(ModelProtocol.CHAT_COMPLETIONS);
        model.setModel("model-name");
        model.setApikey("secret");
        model.setCapabilities(List.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT));
        model.setEnabled(true);
        return model;
    }
}
```

说明：
- import 需补 `ModelTierDefinition`、`RoutePolicyDefinition`、`ModelRuntimeConfigRepository`、`java.util.LinkedHashMap`、`java.util.Map`；`ModelConfigVersionInfo`、`ModelSelectionStrategyType` 与被测类同包或按现有 import 习惯补齐（参考 `ModelConfigCenterTest.java` 的写法）。
- `ModelRuntimeConfigRepositoryHolder` 只是为了传 null 仓储的可读性，可直接传 `null`，删掉该内部类并直接调用 `new ModelConfigCenter(bootstrap, redissonClient, new ObjectMapper(), null, null, changeLogRepository)`。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q test -Dtest=ModelConfigCenterRestoreTest`
Expected: 编译失败（`restoreCanonical` / `listRestoreVersions` / `getRestoreSnapshot` / `getFactoryDefaultConfig` / `findTop500BySuccessTrueOrderByCreatedAtDesc` 不存在）

- [ ] **Step 3: 创建 DTO `ModelConfigVersionInfo`**

```java
package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 模型配置可恢复历史版本条目（来自 model_config_change_log 成功记录）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigVersionInfo {
    private String changeId;
    private Long version;
    private String operatorId;
    private String action;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Repository 增加查询方法**

`src/main/java/com/aseubel/yusi/repository/ModelConfigChangeLogRepository.java`：

```java
package com.aseubel.yusi.repository;

import com.aseubel.yusi.pojo.entity.ModelConfigChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigChangeLogRepository extends JpaRepository<ModelConfigChangeLog, Long> {

    List<ModelConfigChangeLog> findTop500BySuccessTrueOrderByCreatedAtDesc();
}
```

- [ ] **Step 5: ModelConfigCenter 增加恢复内核**

`ModelConfigCenter.java` 变更：

1. 常量区新增：

```java
    private static final String RESTORE_FACTORY_ACTION = "RESTORE_FACTORY";
    private static final String ROLLBACK_ACTION = "ROLLBACK";
```

2. import 补 `com.aseubel.yusi.pojo.dto.model.ModelConfigVersionInfo`、`java.time.LocalDateTime`。

3. `saveChangeLog` / `saveChangeLogSafely` 增加 action 参数（原 `UPDATE_CONFIG` 改为传入），现有调用点传 `UPDATE_CONFIG`：

```java
    private void saveChangeLog(String operatorId, String action, String beforeJson, String afterJson,
            boolean success, String errorMessage) {
        if (changeLogRepository == null) {
            return;
        }
        ModelConfigChangeLog changeLog = ModelConfigChangeLog.builder()
                .changeId(UUID.randomUUID().toString().replace("-", ""))
                .operatorId(operatorId)
                .action(action)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .success(success)
                .errorMessage(errorMessage)
                .build();
        changeLogRepository.save(changeLog);
    }

    private void saveChangeLogSafely(String operatorId, String action, String beforeJson, String afterJson,
            boolean success, String errorMessage) {
        try {
            saveChangeLog(operatorId, action, beforeJson, afterJson, success, truncate(errorMessage));
        } catch (RuntimeException logException) {
            log.warn("Failed to persist model config failure audit: {}", logException.getMessage());
        }
    }
```

`updateVersioned` 中两处调用相应改为 `saveChangeLog(operatorId, UPDATE_CONFIG, beforeJson, afterJson, true, null)` 与 `saveChangeLogSafely(operatorId, UPDATE_CONFIG, beforeJson, afterJson, false, exception.getMessage())`。

4. 新增公开方法（放在 `updateCanonical` / `validateForAdmin` 之后）：

```java
    /** 列出可回滚的历史版本（成功变更按版本号去重、倒序）。 */
    public List<ModelConfigVersionInfo> listRestoreVersions() {
        if (changeLogRepository == null) {
            return List.of();
        }
        Map<Long, ModelConfigVersionInfo> latestByVersion = new LinkedHashMap<>();
        for (ModelConfigChangeLog entry : changeLogRepository.findTop500BySuccessTrueOrderByCreatedAtDesc()) {
            Long version = extractVersion(entry.getAfterJson());
            if (version == null) {
                continue;
            }
            latestByVersion.putIfAbsent(version, ModelConfigVersionInfo.builder()
                    .changeId(entry.getChangeId())
                    .version(version)
                    .operatorId(entry.getOperatorId())
                    .action(entry.getAction())
                    .createdAt(entry.getCreatedAt())
                    .build());
        }
        return List.copyOf(latestByVersion.values());
    }

    /** 读取指定历史版本的脱敏快照（apikey 为掩码），version 重置为当前版本以便草稿预览。 */
    public ModelRoutingProperties getRestoreSnapshot(long version) {
        ModelRoutingProperties snapshot = findSnapshotByVersion(version);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "配置版本不存在: " + version);
        }
        snapshot.setVersion(getCurrentVersion());
        return cloneConfig(snapshot);
    }

    /** 出厂默认配置（YAML bootstrap 副本），version 置为当前版本以便草稿预览。 */
    public ModelRoutingProperties getFactoryDefaultConfig() {
        ModelRoutingProperties factory = cloneConfig(bootstrapProperties);
        factory.setVersion(getCurrentVersion());
        return factory;
    }

    /**
     * 以服务端持有的目标快照恢复整份配置：跳过 stable model id 校验（回滚允许移除新增模型），
     * 其余校验全保留；密钥按 modelId 从当前配置回填；审计 action 区分 RESTORE_FACTORY / ROLLBACK。
     */
    @Transactional(noRollbackFor = ModelRuntimePublishException.class)
    public ModelRoutingProperties restoreCanonical(ModelRoutingProperties target, long expectedVersion,
            String operatorId, boolean factory) {
        return restoreVersioned(target, expectedVersion, operatorId, factory);
    }

    private synchronized ModelRoutingProperties restoreVersioned(ModelRoutingProperties target,
            long expectedVersion, String operatorId, boolean factory) {
        if (target == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "恢复目标配置不能为空");
        }
        ModelRoutingProperties current = currentConfigForWrite();
        if (expectedVersion != current.getVersion()) {
            throw new BusinessException(ErrorCode.CONFIG_VERSION_CONFLICT,
                    "配置版本已过期，当前版本为 " + current.getVersion()
                            + ", 提交版本为 " + expectedVersion);
        }

        ModelRoutingProperties merged = mergeSecrets(cloneConfig(target), current);
        validate(merged);
        merged.setVersion(current.getVersion() + 1);

        String action = factory ? RESTORE_FACTORY_ACTION : ROLLBACK_ACTION;
        String beforeJson = toAuditJson(current);
        String afterJson = toAuditJson(merged);
        try {
            saveRuntimeSnapshot(merged, operatorId);
            saveChangeLog(operatorId, action, beforeJson, afterJson, true, null);
            publishRuntimeConfig(merged);
        } catch (ModelRuntimePublishException exception) {
            saveChangeLogSafely(operatorId, action, beforeJson, afterJson, false, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            saveChangeLogSafely(operatorId, action, beforeJson, afterJson, false, exception.getMessage());
            throw exception;
        }

        applyLocal(merged);
        return cloneConfig(merged);
    }

    private ModelRoutingProperties findSnapshotByVersion(long version) {
        if (changeLogRepository == null) {
            return null;
        }
        for (ModelConfigChangeLog entry : changeLogRepository.findTop500BySuccessTrueOrderByCreatedAtDesc()) {
            String afterJson = entry.getAfterJson();
            if (afterJson == null || afterJson.isBlank()
                    || !Objects.equals(extractVersion(afterJson), version)) {
                continue;
            }
            try {
                return objectMapper.readValue(afterJson, ModelRoutingProperties.class);
            } catch (JsonProcessingException exception) {
                log.warn("Failed to parse restore snapshot for version {}: {}", version, exception.getMessage());
            }
        }
        return null;
    }

    private Long extractVersion(String afterJson) {
        if (afterJson == null || afterJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(afterJson);
            return root.hasNonNull("version") ? root.get("version").asLong() : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }
```

注意：`readRuntimeConfig` 里有 schema v1 拒绝逻辑（`groups`/`matrix` 字段），历史快照全部来自 v2 发布，无需复用该逻辑；但 `restoreVersioned` 里的 `validate(merged)` 会兜底 schema-version 校验。

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -q test -Dtest=ModelConfigCenterRestoreTest`
Expected: PASS（7 个用例）

- [ ] **Step 7: 全量编译**

Run: `mvn -q test-compile`
Expected: 无错误（`saveChangeLog` 签名变更后 `updateVersioned` 调用点已同步修改）

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/aseubel/yusi/service/ai/model/ModelConfigCenter.java src/main/java/com/aseubel/yusi/repository/ModelConfigChangeLogRepository.java src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigVersionInfo.java src/test/java/com/aseubel/yusi/service/ai/model/ModelConfigCenterRestoreTest.java
git commit -m "feat: add restore kernel to model config center"
```

---

### Task 2: 管理端接口（版本列表 / 恢复预览 / 恢复发布）

**Files:**
- Modify: `src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java`
- Modify: `src/main/java/com/aseubel/yusi/controller/ModelManagementController.java`
- Modify: `src/main/java/com/aseubel/yusi/pojo/constant/SecurityAuditAction.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigRestoreRequest.java`
- Create: `src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigRestoreResponse.java`
- Test: `src/test/java/com/aseubel/yusi/service/ai/model/ModelManagementServiceRestoreTest.java`

**Interfaces:**
- Consumes: Task 1 的 `listRestoreVersions()/getRestoreSnapshot()/getFactoryDefaultConfig()/restoreCanonical(...)`；`ModelGovernanceSnapshot` 及其私有装配方法 `toGovernanceModel/toGovernanceTier/toGovernanceRoute`
- Produces:
  - `GET /api/model/config/versions` → `List<ModelConfigVersionInfo>`
  - `GET /api/model/config/preview?mode=FACTORY|VERSION&version=N` → `ModelGovernanceSnapshot`（仅配置字段，runtimeStates 空）
  - `POST /api/model/config/restore` body `{mode, version?, expectedVersion}` → `ModelConfigRestoreResponse{version, action, missingApiKeyModels}`
  - DTO `ModelConfigRestoreRequest{mode, version, expectedVersion}`、`ModelConfigRestoreResponse{version, action, missingApiKeyModels}`

- [ ] **Step 1: 写失败测试 `ModelManagementServiceRestoreTest`**

```java
package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreRequest;
import com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreResponse;
import com.aseubel.yusi.pojo.dto.model.ModelGovernanceSnapshot;
import com.aseubel.yusi.pojo.dto.model.ModelMetricSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelManagementServiceRestoreTest {

    @Test
    void restorePreviewReturnsSnapshotShapedTargetWithoutRuntimeStates() {
        ModelConfigCenter configCenter = org.mockito.Mockito.mock(ModelConfigCenter.class);
        org.mockito.Mockito.when(configCenter.getEffectiveConfig()).thenReturn(SampleConfig.withExtraModel());
        org.mockito.Mockito.when(configCenter.getFactoryDefaultConfig()).thenReturn(SampleConfig.historical());
        ModelManagementService service = ServiceFactory.create(configCenter);

        ModelGovernanceSnapshot preview = service.getRestorePreview("FACTORY", null);

        assertThat(preview.getModels()).extracting(
                ModelGovernanceSnapshot.ModelGovernanceModel::getId).containsExactly("qwen");
        assertThat(preview.getRuntimeStates()).isEmpty();
    }

    @Test
    void restoreVersionRequiresVersionParameter() {
        ModelManagementService service = ServiceFactory.create(
                org.mockito.Mockito.mock(ModelConfigCenter.class));

        assertThatThrownBy(() -> service.getRestorePreview("VERSION", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("version");
    }

    @Test
    void restoreRejectsUnknownMode() {
        ModelManagementService service = ServiceFactory.create(
                org.mockito.Mockito.mock(ModelConfigCenter.class));
        ModelConfigRestoreRequest request = new ModelConfigRestoreRequest();
        request.setMode("WHATEVER");
        request.setExpectedVersion(1L);

        assertThatThrownBy(() -> service.restoreConfig(request, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FACTORY");
    }

    @Test
    void restoreReportsModelsWithEmptyApiKeyAfterMerge() {
        ModelConfigCenter configCenter = org.mockito.Mockito.mock(ModelConfigCenter.class);
        org.mockito.Mockito.when(configCenter.getEffectiveConfig()).thenReturn(SampleConfig.withExtraModel());
        org.mockito.Mockito.when(configCenter.getRestoreSnapshot(2L)).thenReturn(SampleConfig.historical());
        org.mockito.Mockito.when(configCenter.restoreCanonical(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(false)))
                .thenAnswer(invocation -> {
                    ModelRoutingProperties target = invocation.getArgument(0);
                    // 模拟 mergeSecrets 后的历史快照：extra 模型无密钥
                    return SampleConfig.historical();
                });
        ModelManagementService service = ServiceFactory.create(configCenter);
        ModelConfigRestoreRequest request = new ModelConfigRestoreRequest();
        request.setMode("VERSION");
        request.setVersion(2L);
        request.setExpectedVersion(5L);

        ModelConfigRestoreResponse response = service.restoreConfig(request, "admin-1");

        assertThat(response.getAction()).isEqualTo("ROLLBACK");
        assertThat(response.getVersion()).isEqualTo(6L);
        // historical 样本的 qwen 无 apikey，当前配置的 qwen 也无 apikey，回填后仍为空
        assertThat(response.getMissingApiKeyModels()).containsExactly("qwen");
    }

    /** 统一构造被测服务：仅注入 restore 相关依赖，其余传 null。 */
    private static final class ServiceFactory {
        static ModelManagementService create(ModelConfigCenter configCenter) {
            return new ModelManagementService(
                    null, configCenter, null, null, null, null,
                    new com.fasterxml.jackson.databind.ObjectMapper());
        }
    }

    /** 测试样本配置。 */
    private static final class SampleConfig {
        static ModelRoutingProperties withExtraModel() {
            ModelRoutingProperties config = base();
            config.setVersion(5L);
            config.setModels(List.of(model("qwen"), model("extra")));
            return config;
        }

        static ModelRoutingProperties historical() {
            ModelRoutingProperties config = base();
            config.setVersion(6L);
            config.setModels(List.of(model("qwen"))); // extra 模型在历史版本中不存在
            return config;
        }

        private static ModelRoutingProperties base() {
            ModelRoutingProperties config = new ModelRoutingProperties();
            config.setSchemaVersion(2);
            ModelTierDefinition tier = new ModelTierDefinition();
            tier.setMembers(List.of("qwen"));
            tier.setStrategy(ModelSelectionStrategyType.FAIL_OVER);
            config.setTiers(new java.util.LinkedHashMap<>(java.util.Map.of("balanced", tier)));
            RoutePolicyDefinition route = new RoutePolicyDefinition();
            route.setId("chat");
            route.setScene("chat");
            route.setPrimaryTier("balanced");
            route.setEnabled(true);
            config.setRoutes(List.of(route));
            return config;
        }

        private static ModelRoutingProperties.ModelDefinition model(String id) {
            ModelRoutingProperties.ModelDefinition model = new ModelRoutingProperties.ModelDefinition();
            model.setId(id);
            model.setProvider("openai-compatible");
            model.setProtocol(ModelProtocol.CHAT_COMPLETIONS);
            model.setModel("model-name");
            model.setCapabilities(List.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT));
            model.setEnabled(true);
            return model;
        }
    }
}
```

说明：`restoreReportsModelsWithEmptyApiKeyAfterMerge` 里 mock 的 `restoreCanonical` 返回 `historical()`（qwen 无 apikey），用于驱动 `missingApiKeyModels` 断言。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q test -Dtest=ModelManagementServiceRestoreTest`
Expected: 编译失败（`getRestorePreview` / `restoreConfig` / DTO 不存在）

- [ ] **Step 3: 创建请求/响应 DTO**

`src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigRestoreRequest.java`：

```java
package com.aseubel.yusi.pojo.dto.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模型配置恢复请求：mode=FACTORY 出厂恢复；mode=VERSION 需携带 version。 */
@Data
public class ModelConfigRestoreRequest {

    /** 恢复模式：FACTORY | VERSION */
    @NotNull
    private String mode;

    /** 历史回滚目标版本（mode=VERSION 时必填） */
    private Long version;

    /** 乐观锁：当前配置版本 */
    @NotNull
    private Long expectedVersion;
}
```

`src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigRestoreResponse.java`：

```java
package com.aseubel.yusi.pojo.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 模型配置恢复结果；missingApiKeyModels 为恢复后仍无密钥、需手动补填的模型 ID。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigRestoreResponse {
    private Long version;
    private String action;
    private List<String> missingApiKeyModels;
}
```

- [ ] **Step 4: SecurityAuditAction 增加枚举值**

`SecurityAuditAction.java` 在 `MODEL_RUNTIME_STATE_RESET("model.runtime_state.reset"),` 之后加：

```java
    MODEL_CONFIG_RESTORED("model.config.restored"),
```

- [ ] **Step 5: ModelManagementService 增加恢复逻辑**

`ModelManagementService.java`：

1. import 补 `com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreRequest`、`ModelConfigRestoreResponse`、`ModelConfigVersionInfo`。

2. `updateGovernance` 方法后新增：

```java
    public List<ModelConfigVersionInfo> listConfigVersions() {
        return modelConfigCenter.listRestoreVersions();
    }

    public ModelGovernanceSnapshot getRestorePreview(String mode, Long version) {
        ModelRoutingProperties target = resolveRestoreTarget(mode, version);
        return toRestorePreviewSnapshot(target);
    }

    public ModelConfigRestoreResponse restoreConfig(ModelConfigRestoreRequest request, String operatorId) {
        boolean factory = isFactoryMode(request.getMode());
        if (!factory && request.getVersion() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "历史回滚必须指定 version");
        }
        ModelRoutingProperties target = resolveRestoreTarget(request.getMode(), request.getVersion());
        ModelRoutingProperties restored = modelConfigCenter.restoreCanonical(target,
                request.getExpectedVersion(), operatorId, factory);
        List<String> missingApiKeyModels = safeModels(restored).stream()
                .filter(model -> model.getApikey() == null || model.getApikey().isBlank())
                .map(ModelRoutingProperties.ModelDefinition::getId)
                .toList();
        String action = factory ? "RESTORE_FACTORY" : "ROLLBACK";
        if (securityAuditService != null && operatorId != null && !operatorId.isBlank()) {
            try {
                securityAuditService.recordAdmin(SecurityAuditAction.MODEL_CONFIG_RESTORED, operatorId, null,
                        SecurityAuditResourceType.MODEL_GOVERNANCE, "active", SecurityAuditOutcome.SUCCESS,
                        SecurityAuditReasonCode.ADMIN_MUTATION,
                        Map.of(
                                SecurityAuditDetailKeys.OPERATION, SecurityAuditOperation.UPDATE.name(),
                                SecurityAuditDetailKeys.VERSION, String.valueOf(restored.getVersion()),
                                "restoreAction", action));
            } catch (RuntimeException auditException) {
                log.warn("Model config restore audit failed: exceptionType={}",
                        com.aseubel.yusi.common.utils.LowSensitivityLogSummary.exceptionType(auditException));
            }
        }
        return ModelConfigRestoreResponse.builder()
                .version(restored.getVersion())
                .action(action)
                .missingApiKeyModels(missingApiKeyModels)
                .build();
    }

    private boolean isFactoryMode(String mode) {
        if ("FACTORY".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            return true;
        }
        if ("VERSION".equalsIgnoreCase(mode == null ? "" : mode.trim())) {
            return false;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "mode 只能是 FACTORY 或 VERSION");
    }

    private ModelRoutingProperties resolveRestoreTarget(String mode, Long version) {
        if (isFactoryMode(mode)) {
            return modelConfigCenter.getFactoryDefaultConfig();
        }
        if (version == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "历史回滚必须指定 version");
        }
        return modelConfigCenter.getRestoreSnapshot(version);
    }

    /** 仅装配配置字段（models/tiers/routes/defaultRoute）的快照，供前端恢复预览复用 createGovernanceDraft。 */
    private ModelGovernanceSnapshot toRestorePreviewSnapshot(ModelRoutingProperties config) {
        Map<String, ModelRoutingProperties.ModelDefinition> modelsById = safeModels(config).stream()
                .filter(model -> model != null && model.getId() != null)
                .collect(Collectors.toMap(ModelRoutingProperties.ModelDefinition::getId,
                        model -> model, (first, ignored) -> first, LinkedHashMap::new));
        Map<String, List<String>> tierIdsByModel = new HashMap<>();
        safeTiers(config).forEach((tierId, tier) -> {
            if (tier != null && tier.getMembers() != null) {
                tier.getMembers().forEach(modelId -> tierIdsByModel
                        .computeIfAbsent(modelId, ignored -> new ArrayList<>()).add(tierId));
            }
        });
        List<RoutePolicyDefinition> routeDefinitions = config.getRoutes() == null
                ? List.of() : config.getRoutes();
        Map<String, List<String>> routeIdsByModel = new HashMap<>();
        routeDefinitions.forEach(route -> routeModelIds(route, config).forEach(modelId -> routeIdsByModel
                .computeIfAbsent(modelId, ignored -> new ArrayList<>()).add(route.getId())));

        return ModelGovernanceSnapshot.builder()
                .version(config.getVersion())
                .schemaVersion(config.getSchemaVersion())
                .defaultScene(config.getDefaultScene())
                .defaultTier(config.getDefaultTier())
                .models(safeModels(config).stream()
                        .map(model -> toGovernanceModel(model, Map.of(),
                                tierIdsByModel.getOrDefault(model.getId(), List.of()),
                                routeIdsByModel.getOrDefault(model.getId(), List.of())))
                        .toList())
                .tiers(safeTiers(config).entrySet().stream()
                        .map(entry -> toGovernanceTier(entry.getKey(), entry.getValue(), modelsById, Map.of()))
                        .toList())
                .routes(routeDefinitions)
                .defaultRoute(config.getDefaultRoute())
                .runtimeStates(List.of())
                .summary(emptyMetrics())
                .lastRefreshedAt(System.currentTimeMillis())
                .routeProjections(List.of())
                .build();
    }
```

注意：`restoreConfig` 中的 audit 细节 key `"restoreAction"` 若 `SecurityAuditDetailKeys` 有更合适的常量则用常量；没有就用字面量（detail map 的 value 均为 String，安全）。

- [ ] **Step 6: Controller 增加三个端点**

`ModelManagementController.java`（`updateConsole` 之后）：

```java
    @GetMapping("/config/versions")
    public Response<List<com.aseubel.yusi.pojo.dto.model.ModelConfigVersionInfo>> configVersions() {
        checkAdmin();
        return Response.success(modelManagementService.listConfigVersions());
    }

    @GetMapping("/config/preview")
    public Response<ModelGovernanceSnapshot> restorePreview(
            @RequestParam String mode, @RequestParam(required = false) Long version) {
        checkAdmin();
        return Response.success(modelManagementService.getRestorePreview(mode, version));
    }

    @PostMapping("/config/restore")
    @RateLimiter(key = "model-config-restore", time = 60, count = 5, limitType = LimitType.USER)
    public Response<com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreResponse> restoreConfig(
            @Valid @RequestBody com.aseubel.yusi.pojo.dto.model.ModelConfigRestoreRequest request) {
        checkAdmin();
        return Response.success(modelManagementService.restoreConfig(request, UserContext.getUserId()));
    }
```

（实现时把全限定名提升为 import，与文件现有风格一致。）

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn -q test -Dtest=ModelManagementServiceRestoreTest`
Expected: PASS

- [ ] **Step 8: 全量编译 + 既有测试回归**

Run: `mvn -q test-compile && mvn -q test -Dtest='ModelConfigCenterTest,ModelManagementServiceRestoreTest,ModelConfigCenterRestoreTest'`
Expected: 全部 PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/aseubel/yusi/service/ai/model/ModelManagementService.java src/main/java/com/aseubel/yusi/controller/ModelManagementController.java src/main/java/com/aseubel/yusi/pojo/constant/SecurityAuditAction.java src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigRestoreRequest.java src/main/java/com/aseubel/yusi/pojo/dto/model/ModelConfigRestoreResponse.java src/test/java/com/aseubel/yusi/service/ai/model/ModelManagementServiceRestoreTest.java
git commit -m "feat: expose model config restore endpoints"
```

---

### Task 3: 前端恢复入口（api + ModelManagement 页面 + i18n）

**Files:**
- Modify: `frontend/src/lib/api.ts`（modelApi，约 L808-823）
- Modify: `frontend/src/pages/admin/ModelManagement.tsx`
- Modify: `frontend/src/i18n/locales/zh.json`、`frontend/src/i18n/locales/en.json`（`modelManagement` 下新增 `restore` 段，参考 L1976 `advanced` 段位置）

**Interfaces:**
- Consumes: Task 2 的三个 HTTP 端点；`createGovernanceDraft(snapshot)`（modelRouting.ts，直接复用——preview 响应就是 snapshot 形状）
- Produces: 页面上的「恢复出厂默认」「历史版本」入口 + 恢复确认通知条

- [ ] **Step 1: api.ts 增加 modelApi 方法**

`modelApi` 对象（L808-823）内追加：

```ts
  getConfigVersions: () => api.get<ApiResponse<ModelConfigVersionInfo[]>>("/model/config/versions"),
  getRestorePreview: (mode: "FACTORY" | "VERSION", version?: number) =>
    api.get<ApiResponse<ModelGovernanceSnapshot>>("/model/config/preview", { params: { mode, version } }),
  restoreConfig: (data: ModelConfigRestoreRequest) =>
    api.post<ApiResponse<ModelConfigRestoreResponse>>("/model/config/restore", data),
```

并在 `modelApi` 定义之前补类型：

```ts
export interface ModelConfigVersionInfo {
  changeId: string;
  version: number;
  operatorId?: string | null;
  action: string;
  createdAt?: string | null;
}

export interface ModelConfigRestoreRequest {
  mode: "FACTORY" | "VERSION";
  version?: number;
  expectedVersion: number;
}

export interface ModelConfigRestoreResponse {
  version: number;
  action: "RESTORE_FACTORY" | "ROLLBACK";
  missingApiKeyModels: string[];
}
```

- [ ] **Step 2: ModelManagement.tsx 增加恢复状态与处理函数**

组件内（`conflict` state 之后）新增 state：

```tsx
const [restoreVersions, setRestoreVersions] = useState<ModelConfigVersionInfo[] | null>(null)
const [restoreVersionsLoading, setRestoreVersionsLoading] = useState(false)
const [restoreBusy, setRestoreBusy] = useState(false)
// 挂起的恢复操作：草稿当前展示的是恢复预览
const [pendingRestore, setPendingRestore] = useState<{ mode: 'FACTORY' | 'VERSION'; version?: number } | null>(null)
```

import 补 `History, RotateCcw, X` from 'lucide-react' 与 `type ModelConfigVersionInfo, type ModelConfigRestoreResponse` from '../../lib/api'。

处理函数（`resetAllModels` 之后）：

```tsx
const loadRestoreVersions = async () => {
  setRestoreVersionsLoading(true)
  try {
    const response = await modelApi.getConfigVersions()
    setRestoreVersions(response.data.data ?? [])
  } catch {
    toast.error(t('modelManagement.restore.versionsLoadFailed'))
  } finally {
    setRestoreVersionsLoading(false)
  }
}

// 载入恢复目标到草稿（仅预览，不发布）
const loadRestorePreview = async (mode: 'FACTORY' | 'VERSION', version?: number) => {
  try {
    const response = await modelApi.getRestorePreview(mode, version)
    const target = response.data.data
    if (!target) throw new Error(t('modelManagement.restore.previewFailed'))
    const nextDraft = createGovernanceDraft(target)
    setDraft(nextDraft)
    setSelectedRouteId(nextDraft.routes[0]?.id ?? null)
    setPreview(null)
    setPreviewSignature('')
    setPendingRestore({ mode, version })
  } catch (loadError) {
    toast.error(loadError instanceof Error && loadError.message ? loadError.message : t('modelManagement.restore.previewFailed'))
  }
}

// 恢复目标中存在当前配置没有的模型 → 这些模型发布后密钥为空，需提示
const modelsMissingInCurrent = useMemo(() => {
  if (!pendingRestore || !draft || !snapshot) return []
  const currentIds = new Set(snapshot.models.map((model) => model.id))
  return draft.models.filter((model) => !currentIds.has(model.id)).map((model) => model.id)
}, [pendingRestore, draft, snapshot])

const confirmRestore = async () => {
  if (!pendingRestore || !draft) return
  setRestoreBusy(true)
  try {
    const response = await modelApi.restoreConfig({
      mode: pendingRestore.mode,
      version: pendingRestore.version,
      expectedVersion: snapshot?.version ?? 0,
    })
    const result: ModelConfigRestoreResponse | undefined = response.data.data
    setPendingRestore(null)
    toast.success(t('modelManagement.restore.restored', { version: result?.version ?? 0 }))
    if (result?.missingApiKeyModels?.length) {
      toast.warning(t('modelManagement.restore.missingKeys', { models: result.missingApiKeyModels.join(', ') }), { duration: 10000 })
    }
    await loadConsole(true)
  } catch (restoreError) {
    if (isConflict(restoreError)) {
      toast.error(t('modelManagement.console.conflict'))
    } else {
      toast.error(restoreError instanceof Error ? restoreError.message : t('modelManagement.restore.restoreFailed'))
    }
  } finally {
    setRestoreBusy(false)
  }
}
```

- [ ] **Step 3: 恢复通知条 + 高级区入口 UI**

在 `conflict` 通知条之后插入恢复挂起通知条：

```tsx
{pendingRestore && <div className="flex flex-col gap-3 border border-amber-300 bg-amber-50 px-3 py-3 text-sm text-amber-800 dark:border-amber-400/30 dark:bg-amber-400/15 dark:text-amber-200 sm:px-4 md:flex-row md:items-center md:justify-between"><div className="flex items-start gap-2"><AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" /><div><span>{pendingRestore.mode === 'FACTORY' ? t('modelManagement.restore.pendingFactory') : t('modelManagement.restore.pendingVersion', { version: pendingRestore.version })}</span>{modelsMissingInCurrent.length > 0 && <p className="mt-1 text-xs font-medium">{t('modelManagement.restore.missingKeys', { models: modelsMissingInCurrent.join(', ') })}</p>}</div></div><div className="flex flex-col gap-2 sm:flex-row sm:shrink-0"><Button size="sm" onClick={() => void confirmRestore()} disabled={restoreBusy} isLoading={restoreBusy} className="min-h-11 sm:min-h-9"><RotateCcw className="h-4 w-4" aria-hidden="true" />{t('modelManagement.restore.execute')}</Button><Button variant="ghost" size="sm" className="min-h-11 sm:min-h-9" onClick={() => { setPendingRestore(null); void loadConsole(true) }}>{t('modelManagement.restore.cancel')}</Button></div></div>}
```

在 `advancedOpen` 区块（`<pre>` 之前、标题行之后）追加恢复入口：

```tsx
<div className="mt-4 flex flex-col gap-2 border-t border-border pt-3 sm:flex-row sm:items-center sm:justify-between"><div><h4 className="flex items-center gap-2 text-sm font-semibold"><History className="h-4 w-4 text-primary" aria-hidden="true" />{t('modelManagement.restore.title')}</h4><p className="mt-1 text-xs text-muted-foreground">{t('modelManagement.restore.description')}</p></div><div className="flex flex-col gap-2 sm:flex-row"><Button variant="outline" size="sm" onClick={() => void loadRestorePreview('FACTORY')} className="min-h-11 w-full gap-2 sm:min-h-9 sm:w-auto"><RotateCcw className="h-4 w-4" aria-hidden="true" />{t('modelManagement.restore.factory')}</Button><Button variant="outline" size="sm" onClick={() => { if (restoreVersions == null) void loadRestoreVersions() }} className="min-h-11 w-full gap-2 sm:min-h-9 sm:w-auto"><History className="h-4 w-4" aria-hidden="true" />{t('modelManagement.restore.history')}</Button></div></div>
{advancedOpen && restoreVersions != null && <div className="mt-3 max-h-60 space-y-2 overflow-auto border border-border bg-background p-2">{restoreVersions.length === 0 && <p className="p-2 text-xs text-muted-foreground">{t('modelManagement.restore.empty')}</p>}{restoreVersions.map((item) => <div key={item.changeId} className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-xs"><div className="flex flex-col"><span className="font-mono font-medium">v{item.version}</span><span className="text-muted-foreground">{item.action} · {item.operatorId || '-'} · {item.createdAt ? new Date(item.createdAt).toLocaleString() : '-'}</span></div><Button variant="outline" size="sm" className="min-h-9" onClick={() => void loadRestorePreview('VERSION', item.version)}>{t('modelManagement.restore.load')}</Button></div>)}</div>}
```

注意：历史版本列表放在 `advancedOpen &&` 条件内（与 `<pre>` 同级），`restoreVersions != null` 时才渲染列表。

- [ ] **Step 4: i18n 文案**

`zh.json` 的 `modelManagement` 内（`advanced` 段之后）：

```json
"restore": {
  "title": "配置恢复",
  "description": "恢复目标会先载入草稿供预览，确认后才发布生效。",
  "factory": "恢复出厂默认",
  "history": "历史版本",
  "load": "载入预览",
  "execute": "执行恢复",
  "cancel": "取消",
  "pendingFactory": "当前草稿为出厂默认预览，确认后整份覆盖现有配置。",
  "pendingVersion": "当前草稿为历史版本 v{{version}} 预览，确认后整份回滚。",
  "missingKeys": "以下模型恢复后缺少 API Key，需手动补填：{{models}}",
  "restored": "配置已恢复，当前版本 v{{version}}",
  "restoreFailed": "恢复失败，请稍后重试",
  "previewFailed": "恢复目标加载失败",
  "versionsLoadFailed": "历史版本加载失败",
  "empty": "暂无可回滚的历史版本"
}
```

`en.json` 同位置：

```json
"restore": {
  "title": "Config restore",
  "description": "Restore targets load into the draft for preview first, and only take effect after confirmation.",
  "factory": "Restore factory defaults",
  "history": "Version history",
  "load": "Load preview",
  "execute": "Apply restore",
  "cancel": "Cancel",
  "pendingFactory": "The draft shows factory defaults preview; applying will overwrite the whole config.",
  "pendingVersion": "The draft shows historical version v{{version}}; applying will roll back the whole config.",
  "missingKeys": "These models will have no API key after restore and need manual input: {{models}}",
  "restored": "Config restored, now at v{{version}}",
  "restoreFailed": "Restore failed, please retry",
  "previewFailed": "Failed to load restore target",
  "versionsLoadFailed": "Failed to load version history",
  "empty": "No restorable versions yet"
}
```

- [ ] **Step 5: 前端编译验证**

Run: `pnpm -C frontend build`
Expected: 无类型错误、构建成功

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/pages/admin/ModelManagement.tsx frontend/src/i18n/locales/zh.json frontend/src/i18n/locales/en.json
git commit -m "feat: add config restore entry to model management console"
```

---

### Task 4: 端到端手动验证与收尾

**Files:**
- 无新增；运行验证

**Interfaces:**
- Consumes: Task 1-3 全部产物
- Produces: 验证通过的记录（写进本次 record 文档）

- [ ] **Step 1: 后端全量编译 + 相关单测**

Run: `mvn -q test-compile && mvn -q test -Dtest='ModelConfigCenterTest,ModelConfigCenterRestoreTest,ModelManagementServiceRestoreTest'`
Expected: 全部 PASS

- [ ] **Step 2: 本地起服务手动验证链路**

启动后端（`./run-backend.ps1` 或既有方式），用管理员账号验证：

1. `GET /api/model/config/versions` 返回历史版本列表（若 DB 无成功变更记录则返回空数组）
2. 控制台 → 高级快照 → 「历史版本」→ 任选版本「载入预览」→ 出现琥珀色通知条，草稿内容变为历史配置
3. 通知条点「执行恢复」→ 版本 +1，控制台刷新为恢复后配置
4. 恢复包含当前不存在模型的历史版本 → 通知条与 toast 均提示缺 Key 模型列表
5. 「恢复出厂默认」→ 预览 → 执行 → 审计表 `model_config_change_log` 新增 action=`RESTORE_FACTORY` 记录；历史回滚为 `ROLLBACK`
6. 双开页面制造版本冲突 → 恢复时报冲突提示（`CONFIG_VERSION_CONFLICT`）

- [ ] **Step 3: 写 record 文档**

`docs/record/2026-MM-DD-model-config-restore.md`：记录动机、方案（方案 A：服务端 restore 端点 + 草稿预览）、密钥回填机制、审计 action 约定、验证结果。

- [ ] **Step 4: Commit & Push**

```bash
git add docs/record/
git commit -m "docs: record model config restore validation"
git push
```
