package com.aseubel.yusi.redis.common;

/**
 * @author Aseubel
 * @date 2026/1/10 下午5:08
 */
public class RedisKey {
    public static final String APP = "yusi";
    public static final String SEPARATOR = ":";
    public static final String REDIS_KEY_PREFIX = "yusi" + SEPARATOR;

    public static final String AUTH_PREFIX = REDIS_KEY_PREFIX + "auth" + SEPARATOR;
    public static final String REFRESH_TOKEN_KEY = AUTH_PREFIX + "refresh:";
    public static final String BLACKLIST_KEY = AUTH_PREFIX + "blacklist:";
    public static final String DEVICE_TOKENS_KEY = AUTH_PREFIX + "devices:";

    public static final String USAGE_PREFIX = REDIS_KEY_PREFIX + "usage" + SEPARATOR;

    public static final String MODEL_PREFIX = REDIS_KEY_PREFIX + "model" + SEPARATOR;
    public static final String MODEL_STATE_MAP = MODEL_PREFIX + "state:instances";
    public static final String MODEL_GROUP_STRATEGY_MAP = MODEL_PREFIX + "group:strategies";
    public static final String MODEL_STATE_CHANNEL = MODEL_PREFIX + "state:channel";
    public static final String MODEL_GROUP_STRATEGY_CHANNEL = MODEL_PREFIX + "group:strategy:channel";
    public static final String MODEL_RUNTIME_CONFIG_KEY = MODEL_PREFIX + "runtime:config";
    public static final String MODEL_CONFIG_CHANNEL = MODEL_PREFIX + "config:channel";

    public static final String VIOLATION_PREFIX = REDIS_KEY_PREFIX + "violation" + SEPARATOR;
    public static final String VIOLATION_COUNT_KEY = VIOLATION_PREFIX + "count:%s";
}
