# OSS SDK 异常包装导致删除失败被误归类

日期：2026-08-28

## 现象

本地环境注销用户时，清理 OSS 对象前缀报
`OperationException: Operation ListObjectsV2 raised an exception ... Error Code: NoSuchBucket`，
删除流程标记 `PENDING_RETRY(external_or_database)`，接口返回 50002。日志中的失败类别指向
外部/数据库层，掩盖了「bucket 不存在」这一简单事实。

## 根因

两点叠加：

1. aliyun oss2 SDK 的同步客户端把远端 `ServiceException` 包装在
   `com.aliyun.sdk.service.oss2.exceptions.OperationException` 里抛出；只 catch
   `ServiceException` 永远接不到远端错误码，必须解开 cause 链。
2. 语义层面：bucket 不存在意味着该前缀下必然没有对象，清理目标已经达成，
   不应作为可重试的外部故障反复排队。

本地/隔离环境常用占位 bucket（`placeholder-local.oss-...`），该场景必现。

## 修复

`OssService.deleteOwnedObjectPrefix` 捕获 `OperationException` 后沿 cause 链查找
`ServiceException`，错误码为 `NoSuchBucket` 时按「无对象可删」直接返回（日志标记
`reason=no_such_bucket`），其余异常原样抛出。

## 验证

- 后端日志出现 IMAGE/AUDIO/CHUNK 三类 `OSS prefix cleanup skipped ... reason=no_such_bucket`。
- E2E 基准注销收尾从 `PENDING_RETRY(external_or_database)` 变为通过（与竞态修复叠加后 COMPLETED）。
