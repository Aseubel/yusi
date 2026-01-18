package com.aseubel.yusi.redis.aspect;

import com.aseubel.yusi.redis.annotation.SpelResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @date 2025/8/9 上午10:40
 */
@Aspect
@Component
public class SpelResolverAspect {

    private static final Logger logger = LoggerFactory.getLogger(SpelResolverAspect.class);

    private final ExpressionParser parser = new SpelExpressionParser();

    // 使用新的、推荐的 ParameterNameDiscoverer
    // 它在 Spring Boot 3 / Spring Framework 6 中是首选
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(spelResolver)")
    public Object resolveSpel(ProceedingJoinPoint joinPoint, SpelResolver spelResolver) throws Throwable {

        String spelExpression = spelResolver.expression();
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
            logger.info("SpEL Expression: '{}' resolved to: '{}' [Type: {}]",
                    spelExpression,
                    resolvedValue,
                    resolvedValue != null ? resolvedValue.getClass().getSimpleName() : "null");

        } catch (Exception e) {
            logger.error("Failed to resolve SpEL expression: '{}'", spelExpression, e);
        }

        return joinPoint.proceed();
    }
}
