package com.aseubel.yusi.service.ai.mask;

/**
 * 敏感实体类型枚举
 * <p>
 * NLP 识别类型（HanLP 词性标注）和正则识别类型的统一定义。
 */
public enum SensitiveEntityType {

    /** 人名 - HanLP 词性 nr */
    PERSON("nr", "[人名_%d]"),
    /** 地名 - HanLP 词性 ns */
    LOCATION("ns", "[地名_%d]"),
    /** 机构名 - HanLP 词性 nt */
    ORGANIZATION("nt", "[机构_%d]"),
    /** 电话号码 - 正则识别 */
    PHONE("PHONE", "[电话_%d]"),
    /** 邮箱地址 - 正则识别 */
    EMAIL("EMAIL", "[邮箱_%d]"),
    /** 身份证号 - 正则识别 */
    ID_CARD("ID_CARD", "[证件_%d]");

    private final String tag;
    private final String template;

    SensitiveEntityType(String tag, String template) {
        this.tag = tag;
        this.template = template;
    }

    public String getTag() {
        return tag;
    }

    /**
     * 生成占位符，如 [人名_1]
     */
    public String getPlaceholder(int index) {
        return String.format(template, index);
    }

    /**
     * 根据 HanLP 词性标签查找对应的实体类型
     *
     * @param natureTag HanLP 词性字符串（如 "nr"）
     * @return 对应的类型，未匹配返回 null
     */
    public static SensitiveEntityType fromNatureTag(String natureTag) {
        if (natureTag == null)
            return null;
        return switch (natureTag) {
            case "nr" -> PERSON;
            case "ns" -> LOCATION;
            case "nt" -> ORGANIZATION;
            default -> null;
        };
    }
}
