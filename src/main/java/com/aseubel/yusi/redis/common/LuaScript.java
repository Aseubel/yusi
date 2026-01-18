package com.aseubel.yusi.redis.common;

/**
 * @author Aseubel
 * @date 2025/7/11 下午10:51
 */
public class LuaScript {

    public static final String SET_GET_REMOVE_SCRIPT = """
            local key = KEYS[1]
            local members = redis.call('SMEMBERS', key)
            redis.call('DEL', key)
            return members
            """;

    public static final String HASH_GET_REMOVE_SCRIPT = """
            local key = KEYS[1]
            local members = redis.call('HGETALL', key)
            redis.call('DEL', key)
            return members
            """;
}
