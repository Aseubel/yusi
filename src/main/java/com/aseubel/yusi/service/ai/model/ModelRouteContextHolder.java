package com.aseubel.yusi.service.ai.model;

public final class ModelRouteContextHolder {

    private static final ThreadLocal<ModelRouteContext> HOLDER = new ThreadLocal<>();

    private ModelRouteContextHolder() {
    }

    public static void set(ModelRouteContext context) {
        HOLDER.set(context);
    }

    public static ModelRouteContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
