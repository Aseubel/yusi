package com.aseubel.yusi.service.ai.model.provider;

/**
 * 请求级思考模式覆盖（由 route 解析到的 primary-tier 配置决定）。
 *
 * <p>langchain4j 的 OpenAI Responses 请求参数不支持 customParameters，
 * DashScope 兼容参数（如 {@code enable_thinking}）只能在 HTTP 装饰器层注入，
 * 而 client bundle 是模型级缓存的、不感知场景。因此在模型代理调用线程上
 * 通过 ThreadLocal 传递 tier 覆盖值，DashScope 装饰器发送请求时读取。</p>
 *
 * <p>仅在「设置 → HTTP 请求发送 → 清除」窗口内生效；流式响应回调发生在
 * 其他线程且不再发送请求体，因此不受影响。</p>
 */
public final class ThinkingRequestContext {

    private static final ThreadLocal<Boolean> OVERRIDE = new ThreadLocal<>();

    private ThinkingRequestContext() {
    }

    public static Boolean override() {
        return OVERRIDE.get();
    }

    public static void setOverride(Boolean thinkingEnabled) {
        if (thinkingEnabled != null) {
            OVERRIDE.set(thinkingEnabled);
        }
    }

    public static void clear() {
        OVERRIDE.remove();
    }
}
