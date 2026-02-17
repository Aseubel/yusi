package com.aseubel.yusi.redis.annotation;

import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 更新缓存注解，支持重复使用
 * 
 * @author Aseubel
 * @date 2025/8/7 上午10:57
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Reflective
@Repeatable(UpdateCache.Container.class)
public @interface UpdateCache {
    @AliasFor("cacheNames")
    String[] value() default {};

    @AliasFor("value")
    String[] cacheNames() default {};

    String key() default "";

    /**
     * 是否仅失效缓存而不更新
     * 默认为 false，即会尝试将返回值写入缓存
     */
    boolean evictOnly() default false;

    /**
     * 容器注解，用于支持 @Repeatable
     */
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    @interface Container {
        UpdateCache[] value();
    }
}
