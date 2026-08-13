package com.aseubel.yusi.service.lifegraph.constant;

/** Evidence categories used to explain why an entity entered LifeGraph. */
public enum LifeGraphEvidenceKind {
    USER("USER"),
    USER_RELATION("USER_RELATION"),
    LIFE_ATTRIBUTE("LIFE_ATTRIBUTE");

    private final String code;

    LifeGraphEvidenceKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
