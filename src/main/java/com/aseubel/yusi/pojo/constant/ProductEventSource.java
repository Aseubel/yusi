package com.aseubel.yusi.pojo.constant;

public enum ProductEventSource {
    MATCH("match"),
    CONNECTION("connection"),
    NOTIFICATION("notification"),
    CHAT("chat"),
    MEMORY("memory"),
    SYSTEM("system");

    private final String code;

    ProductEventSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
