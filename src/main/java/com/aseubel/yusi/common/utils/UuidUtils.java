package com.aseubel.yusi.common.utils;

import com.fasterxml.uuid.Generators;

public class UuidUtils {

    /**
     * 生成 UUIDv7 字符串
     * @return uuid string
     */
    public static String genUuid() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    /**
     * 生成不带横杠的 UUIDv7 字符串
     * @return simple uuid string
     */
    public static String genUuidSimple() {
        return genUuid().replace("-", "");
    }
}
