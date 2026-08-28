# 异步认知摄取与注销删除事务竞态

日期：2026-08-28

## 现象

E2E 基准收尾的 `admin-deregister` 步骤返回 50002（账号删除未完成），后端日志
`Admin deregistration pending retry: failureCategory=database_invariant`。注册/日记/聊天等
前置步骤全部通过，且每次运行都在同一收尾步骤失败。

## 根因

注销删除在独立事务内先删子表、最后删 user，再执行 `requireClean` 残留校验。而日记写入触发的
异步认知摄取（`onDiaryCognitionIngest` → `MatchProfileAssembler.refreshProfile`）与删除并发运行，
在删除事务执行期间为同一用户重新 INSERT `match_profile`。时序上落在「DELETE match_profile 之后、
requireClean 之前」的窗口内时，残留校验发现新写入的行，删除按 failure-closed 语义标记
`PENDING_RETRY(database_invariant)`。

竞态窗口由 E2E 旅程时序决定：注销紧随日记写入，异步摄取尚在执行，因此基准上稳定复现。

## 修复

- `InterfaceUsageMonitor` 新增 `isUserSuppressed`：注销流程开始即抑制该用户的写路径。
- `AgentCognitionOrchestratorImpl.ingest` 入口检查抑制状态，直接跳过异步摄取，不创建任务、不写任何表。

## 验证

- `AccountDeletionRaceGuardTest`：H2 触发器在 `DELETE user` 时刻确定性注入 match_profile 残留，
  断言第一次删除 `PENDING_RETRY(database_invariant)` 且 user 行存活；移除并发源后重试收敛
  `COMPLETED` 且零残留。
- `AgentCognitionOrchestratorTest.suppressedUserMustSkipIngestionDuringAccountDeletion`：
  抑制期间 ingest 零副作用（verifyNoInteractions）。
- 重跑 E2E 基准：OSS 修复 + 本修复后 `admin-deregister` 与 `post-deletion-check` 通过。
