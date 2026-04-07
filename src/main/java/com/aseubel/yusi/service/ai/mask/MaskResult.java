package com.aseubel.yusi.service.ai.mask;

import java.util.Map;

/**
 * 脱敏结果
 * <p>
 * 包含脱敏后的文本和映射表，映射表仅在请求级生命周期中使用（内存传递，不存 Redis）。
 */
public class MaskResult {

    /** 脱敏后的文本 */
    private final String maskedText;

    /** 占位符 → 原始值 的映射表，如 {"[人名_1]": "张三", "[电话_1]": "13800138000"} */
    private final Map<String, String> mappingTable;

    /** 是否进行了脱敏 */
    private final boolean hasMasked;

    public MaskResult(String maskedText, Map<String, String> mappingTable, boolean hasMasked) {
        this.maskedText = maskedText;
        this.mappingTable = mappingTable;
        this.hasMasked = hasMasked;
    }

    /** 空文本 */
    public static MaskResult empty() {
        return new MaskResult("", Map.of(), false);
    }

    /** 无需脱敏，原样返回 */
    public static MaskResult noMask(String text) {
        return new MaskResult(text, Map.of(), false);
    }

    public String getMaskedText() {
        return maskedText;
    }

    public Map<String, String> getMappingTable() {
        return mappingTable;
    }

    public boolean isHasMasked() {
        return hasMasked;
    }
}
