package com.aseubel.yusi.common.ratelimit;

import com.aseubel.yusi.common.auth.UserContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterAspectTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void userSubjectIsFixedLengthAndDoesNotExposeTheRawUserId() {
        UserContext.setUserId("fixture-user-rate");

        RateLimiterAspect aspect = new RateLimiterAspect();
        String key = aspect.getCombineKey(annotation("operation", LimitType.USER), joinPoint());

        assertThat(key).doesNotContain("fixture-user-rate");
        assertThat(key).matches(".*:u:[0-9a-f]{64}:.*");
    }

    @Test
    void ipSubjectIsFixedLengthAndDoesNotExposeTheRawIp() {
        RateLimiterAspect aspect = new RateLimiterAspect();
        String key = aspect.getCombineKey(annotation("operation", LimitType.IP), joinPoint());

        assertThat(key).doesNotContain("fixture-object-key-rate");
        assertThat(key).matches(".*:ip:[0-9a-f]{64}:.*");
    }

    @Test
    void aspectHasAnExplicitRateLimitedMetricHook() throws Exception {
        Method method = RateLimiterAspect.class.getDeclaredMethod(
                "recordRateLimited", String.class, String.class);

        assertThat(method).isNotNull();
    }

    private RateLimiter annotation(String key, LimitType limitType) {
        return new RateLimiter() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public int time() {
                return 60;
            }

            @Override
            public int count() {
                return 1;
            }

            @Override
            public LimitType limitType() {
                return limitType;
            }

            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RateLimiter.class;
            }
        };
    }

    private JoinPoint joinPoint() {
        MethodSignature signature = mock(MethodSignature.class);
        Method method = SampleController.class.getDeclaredMethods()[0];
        when(signature.getMethod()).thenReturn(method);
        JoinPoint point = mock(JoinPoint.class);
        when(point.getSignature()).thenReturn(signature);
        return point;
    }

    private static final class SampleController {
        @SuppressWarnings("unused")
        private void operation() {
        }
    }
}
