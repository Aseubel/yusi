package com.aseubel.yusi.redis.annotation;

import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * @author Aseubel
 * @date 2025/8/7 下午8:05
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Reflective
public @interface QueryCache {
    @AliasFor("cacheNames")
    String[] value() default {};

    @AliasFor("value")
    String[] cacheNames() default {};

    /**
     * 缓存的键，支持Spring Expression Language (SpEL)。
     * 例如： "#userId" 或 "#user.id"
     */
    String key() default "";

    SpelResolver spelResolver() default @SpelResolver(expression = "");

    /**
     * 缓存过期时间，单位秒
     * -1 表示使用全局配置
     */
    long ttl() default -1;
}
