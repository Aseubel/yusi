package com.aseubel.yusi.monitor;

import com.aseubel.yusi.common.auth.UserContext;
import com.aseubel.yusi.common.web.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class InterfaceMonitorAspect {

    private final InterfaceUsageMonitor monitor;
    private final ClientIpResolver clientIpResolver;

    @Pointcut("execution(* com.aseubel.yusi.controller..*.*(..))")
    public void controllerPointcut() {
    }

    @Before("controllerPointcut()")
    public void before(JoinPoint joinPoint) {
        try {
            // Get User ID
            String userId = UserContext.getUserId();
            if (userId == null) {
                userId = "anonymous";
            }

            // Get IP
            String ip = "unknown";
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                ip = clientIpResolver.resolve(attributes.getRequest());
            }

            // Get Interface Name
            String className = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();
            String interfaceName = className + "#" + methodName;

            // Record
            monitor.recordUsage(userId, ip, interfaceName);

        } catch (Exception e) {
            // Do not block main logic
            log.error("Error in InterfaceMonitorAspect", e);
        }
    }

}
