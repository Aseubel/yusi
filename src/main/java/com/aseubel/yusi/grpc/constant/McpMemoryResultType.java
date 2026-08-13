package com.aseubel.yusi.grpc.constant;

/** Result type values exposed by the MCP memory search API. */
public enum McpMemoryResultType {
    LONG_TERM_MEMORY("LONG_TERM_MEMORY"),
    SHORT_TERM_CONTEXT("SHORT_TERM_CONTEXT");

    private final String code;

    McpMemoryResultType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
