package com.aseubel.yusi.pojo.constant;

/** Anchor kinds supported by diary attachment bindings. */
public enum DiaryAttachmentAnchorKind {
    TEXT_RANGE("TEXT_RANGE");

    private final String code;

    DiaryAttachmentAnchorKind(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
