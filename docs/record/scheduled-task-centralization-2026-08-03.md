# 定时任务统一管理记录

日期：2026-08-03

## 背景

此前 `@Scheduled` 分散在监控、记忆、匹配、周报、Embedding 和 LifeGraph 等业务类中。多实例部署时，除任务表自身的 claim 机制外，没有统一的 scheduler-level 分布式互斥策略。

## 本次调整

- 在 `common/task/scheduler/YusiScheduledTasks` 集中声明所有 Spring 定时入口。
- 在 `common/task/DistributedJobRunner` 中统一封装 Redisson 任务锁。
- 业务服务保留普通 public 方法，仍可被 API、事件或其他应用流程调用。
- 使用任务名作为锁粒度，未抢到锁的实例跳过本轮，不等待其他实例完成。
- 保留动态的 `memoryConfigProperties.midTermScanCron` 配置。

## 锁策略

使用分布式锁的单实例任务：接口统计同步、记忆兜底扫描、房间清理、中期记忆融合、主动问候、Embedding 清理、LifeGraph 清理、LifeGraph 合并建议、周报和周匹配。

不使用全局锁的多实例任务：Embedding/LifeGraph 待处理任务消费和超时回收，以及模型状态同步。前两类通过数据库任务 claim（`FOR UPDATE SKIP LOCKED`）实现并行消费；模型状态同步必须由每个实例执行，才能发布各自的运行状态。

## 当前边界

这是一套基于 Spring Scheduler + Redisson 锁的集群互斥方案，不是独立的持久化调度平台。任务触发仍依赖每个实例的本地调度器；Redis 不可用时，任务会记录错误并等待下一轮。后续若需要补偿、历史执行记录、动态暂停、租约观测或复杂重试，应评估 Quartz 集群或外部调度平台，而不是继续扩大当前入口类。
