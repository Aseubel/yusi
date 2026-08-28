# H2 触发器内隐式提交被禁

日期：2026-08-28

## 现象

为注销竞态编写确定性回归测试时，在 H2 `BEFORE DELETE` 触发器内用
`connection.prepareStatement(...).executeUpdate()` 插入残留行，触发器一执行就失败，
且失败信息与隐式提交相关（H2 不允许在触发器上下文中 commit/rollback）。

## 根因

测试通过 `DriverManagerDataSource` 获取连接，默认 `autoCommit=true`。H2 触发器 fire 时
拿到的是执行 DELETE 语句的那条连接；在其上执行 DML 时 H2 仍按 autoCommit 语义试图隐式
提交，而触发器上下文禁止提交——语义上触发器写入必须与宿主语句同生共死。

## 修复

`fire` 开头显式 `connection.setAutoCommit(false)`，让触发器写入留在宿主 DELETE 语句的
事务上下文中；不主动 commit（宿主语句的提交/回滚决定触发器写入的命运）。

## 验证

`AccountDeletionRaceGuardTest`：注入的 match_profile 残留被 `requireClean` 捕获
（`PENDING_RETRY(database_invariant)`），证明写入与删除语句处于同一上下文且对外可见时机正确。

## 复盘要点

- H2 触发器内做 DML 必须 `setAutoCommit(false)`，且不要在 fire 内提交。
- 用触发器在事务中点注入并发写入，是重放「DELETE 之后、校验之前」竞态窗口的
  确定性手段，优于后台线程 + sleep 的 flaky 方案。
