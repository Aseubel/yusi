# Profile 化配置类的注册点导致 test 上下文缺 bean

日期：2026-08-28

## 现象

46 个 `@SpringBootTest` 全上下文测试集体失败：`Failed to load ApplicationContext`，
root cause 为 `MilvusCollectionProperties` 无可用 bean。生产启动完全正常，
仅 test profile 崩溃。

## 根因

`MilvusCollectionProperties` 通过 `MilvusConfig` 上的
`@EnableConfigurationProperties` 注册，而 `MilvusConfig` 标注 `@Profile("!test")`。
benchmark 改造新增的 `MidTermMemoryVectorService` 无条件注入该 properties——
test profile 下配置类整体不加载、properties 缺注册，但依赖它的服务仍被组件扫描加载，
上下文组装失败。

结构性问题：**配置属性的注册点继承了宿主配置类的 profile 语义**，
新增跨 profile 消费者时没有任何机制提示这种隐性耦合。

## 修复

新建无 profile 限制的 `MilvusCollectionConfig`（唯一 `@EnableConfigurationProperties`
注册点），从 `MilvusConfig` 的注册列表中移除该 properties。类注释明确说明
不能加 `@Component`（会产生与 @EnableConfigurationProperties 的同类型 bean 歧义），
注册点必须唯一。

## 验证

全量 564 个测试通过（此前 46 个上下文失败全部消除）。

## 复盘要点

`@EnableConfigurationProperties` 挂在带 `@Profile` 的配置类上时，注册行为随 profile
消失；属性类若被跨 profile 消费，注册点必须放在无 profile 的配置类或启动类上。
