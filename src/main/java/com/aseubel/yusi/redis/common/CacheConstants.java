package com.aseubel.yusi.redis.common;

/**
 * @author Aseubel
 * @date 2025/8/7 上午11:02
 */
public class CacheConstants {

    // 私有构造函数，防止实例化
    private CacheConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 常量定义
    public static final String VALUE = "value";
    public static final String UNLOCK_TIME = "unlockTime";
    public static final String OWNER = "owner";
    public static final String LOCK_INFO = "lockInfo";
    public static final String NEED_QUERY = "NEED_QUERY";
    public static final String NEED_WAIT = "NEED_WAIT";
    public static final String SUCCESS_NEED_QUERY = "SUCCESS_NEED_QUERY";
    public static final String SUCCESS = "SUCCESS";
    public static final String EMPTY_VALUE_SUCCESS = "EMPTY_VALUE_SUCCESS";

}
