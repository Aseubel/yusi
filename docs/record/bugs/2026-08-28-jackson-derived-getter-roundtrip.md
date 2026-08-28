# Jackson 派生 getter 破坏配置对象往返

日期：2026-08-28

## 现象

`ModelConfigCenterTest` 3 个用例失败：`cloneConfig`（用 Jackson `convertValue` 做
深拷贝/草稿投影）抛 `IllegalArgumentException: Unrecognized field "thinkingDisabled"`。
测试 fixture 与生产配置文件里都没有 `thinkingDisabled` 字段。

## 根因

`ModelRoutingProperties.ModelDefinition` 为禁思考改造新增了派生便捷方法：

```java
public boolean isThinkingDisabled() { return Boolean.FALSE.equals(thinkingEnabled); }
```

Jackson 按 bean 约定把 `isThinkingDisabled()` 识别为 `thinkingDisabled` 属性参与序列化；
`convertValue` 往返时输出端多出该字段，输入端没有对应 setter/字段，默认
`FAIL_ON_UNKNOWN_PROPERTIES` 直接失败。派生 getter 的存在使「能序列化出去的字段集合」
大于「能反序列化回来的字段集合」，任何 round-trip 场景都会炸。

## 修复

派生方法加 `@JsonIgnore`（保留给 Java 侧便捷判断使用，不参与序列化）：
序列化往返只走持久化字段 `thinkingEnabled`。

## 验证

`ModelConfigCenterTest` 9 个用例通过。

## 复盘要点

为 @Data 配置类添加 `isXxx()`/`getXxx()` 形式的派生方法前，先确认它是否应进入
JSON 契约；凡是 `convertValue`/`readValue(writeValueAsString(x))` 的路径，
派生 getter 必须显式 `@JsonIgnore`，否则字段集合不对称。
