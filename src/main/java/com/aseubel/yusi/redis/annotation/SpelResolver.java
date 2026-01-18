package com.aseubel.yusi.redis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义SpEL解析注解。
 * 用于标记一个方法，其注解中的表达式将在方法执行前被解析。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpelResolver {

    /**
     * 需要被解析的Spring Expression Language (SpEL)表达式。
     * @return SpEL表达式字符串
     */
    String expression();
}
