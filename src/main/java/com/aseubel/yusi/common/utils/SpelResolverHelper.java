package com.aseubel.yusi.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * @author Aseubel
 * @date 2025/8/9 下午2:52
 */
@Slf4j
@Component
public class SpelResolverHelper {

    private final ExpressionParser parser = new SpelExpressionParser();

    // 使用新的、推荐的 ParameterNameDiscoverer
    // 它在 Spring Boot 3 / Spring Framework 6 中是首选
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    public Object resolveSpel(ProceedingJoinPoint joinPoint, String spelExpression) throws Throwable {

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();

        // discoverer.getParameterNames(method) 的用法保持不变
        String[] parameterNames = discoverer.getParameterNames(method);

        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        try {
            Object resolvedValue = parser.parseExpression(spelExpression).getValue(context);
            log.info("SpEL Expression: '{}' resolved to: '{}' [Type: {}]",
                    spelExpression,
                    resolvedValue,
                    resolvedValue != null ? resolvedValue.getClass().getSimpleName() : "null");
            return resolvedValue;
        } catch (Exception e) {
            log.error("Failed to resolve SpEL expression: '{}'", spelExpression, e);
            throw new IllegalArgumentException("Failed to resolve SpEL expression: " + spelExpression, e);
        }
    }
}
