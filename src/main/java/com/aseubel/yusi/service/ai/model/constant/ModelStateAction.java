package com.aseubel.yusi.service.ai.model.constant;

/** Model runtime state event actions. */
public enum ModelStateAction {
    PHASE_CHANGE("PHASE_CHANGE");

    private final String code;

    ModelStateAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
