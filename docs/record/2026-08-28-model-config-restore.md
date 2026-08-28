# 模型配置恢复（出厂默认 + 历史回滚）记录

日期：2026-08-28

## 动机

模型治理控制台此前只能整份编辑发布，误改后无法快速回到出厂配置或历史版本。本次为控制台增加"恢复出厂默认"与"回滚到历史版本"两个整份配置恢复能力。

## 方案（方案 A：服务端 restore 端点 + 草稿预览）

- 恢复目标一律由服务端读取：历史快照来自 `model_config_change_log.after_json`，出厂默认来自 YAML 绑定的 `bootstrapProperties`，不信任前端 draft。
- 前端恢复目标仅作为草稿预览载入控制台草稿区，确认后走 `POST /api/model/config/restore` 由服务端发布。
- `ModelConfigCenter` 新增 `restoreCanonical` 发布路径：复用 校验→版本+1→`model_runtime_config` 快照落库→`model_config_change_log` 审计→Redis 发布→本地生效 链路，跳过 `validateStableModelIds`（回滚允许移除后来新增的模型），其余校验全部保留。
- 版本乐观锁：`expectedVersion` 不匹配当前版本时抛 `CONFIG_VERSION_CONFLICT`。

## 密钥回填机制

历史快照是脱敏 JSON（apikey 为 `******`），绝不能把掩码当作真实密钥落库。发布前经 `mergeSecrets` 按 modelId 从当前配置回填密钥；恢复目标中当前已不存在的模型密钥为空，接口返回 `missingApiKeyModels` 列表，前端在通知条与 toast 中明确提示需手动补填。

## 审计 action 约定

- 出厂恢复记 `RESTORE_FACTORY`，历史回滚记 `ROLLBACK`，不记 `UPDATE_CONFIG`（`model_config_change_log.action`）。
- 管理员动作通过 `security_audit_event` 记 `MODEL_CONFIG_RESTORED`（details 含 operation/version/action，均为低敏字段）。

## 接口

- `GET /api/model/config/versions` — 可回滚历史版本列表（最近 500 条成功变更按 version 去重倒序）。
- `GET /api/model/config/preview?mode=FACTORY|VERSION&version=N` — 恢复目标预览（snapshot 形状，runtimeStates 为空）。
- `POST /api/model/config/restore` — body `{mode, version?, expectedVersion}`，返回 `{version, action, missingApiKeyModels}`；限流 5 次/分钟/用户。

## 验证结果

- `mvn -q test-compile` 通过；`ModelConfigCenterRestoreTest`（7 用例）与 `ModelManagementServiceRestoreTest`（4 用例）及既有 `ModelConfigCenterTest` 全部 PASS。
- `pnpm -C frontend build` 通过（tsc -b + vite build 无类型错误）。
- 已覆盖：恢复版本 +1、RESTORE_FACTORY/ROLLBACK 审计 action 区分、回滚移除新增模型放行、密钥按 modelId 回填、版本冲突拒绝、未知版本拒绝、版本列表去重倒序、恢复预览 snapshot 形状（runtimeStates 空）、mode 合法性校验、恢复后缺密钥模型上报。
- 前端入口位于控制台「高级快照」区块：恢复出厂默认 / 历史版本列表 / 载入预览 / 琥珀色挂起通知条（执行恢复 / 取消）。
- 待人工端到端验证：本地起服务后按计划 Task 4 Step 2 清单操作（版本列表、预览、执行恢复、缺 Key 提示、审计落库、双开页面版本冲突）。
