package com.aseubel.yusi.controller;

import com.aseubel.yusi.service.key.KeyManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KeyManagementRecoveryContractTest {

    @Test
    void exposesAuthenticatedRecoveryEndpoints() {
        assertThat(methodNames(KeyManagementController.class))
                .contains("sendRecoveryCode", "recoverKey");
    }

    @Test
    void usesDedicatedRecoveryPaths() throws Exception {
        PostMapping sendCode = KeyManagementController.class
                .getDeclaredMethod("sendRecoveryCode")
                .getAnnotation(PostMapping.class);
        PostMapping recover = KeyManagementController.class
                .getDeclaredMethod("recoverKey", com.aseubel.yusi.pojo.dto.key.KeyRecoveryRequest.class)
                .getAnnotation(PostMapping.class);

        assertThat(sendCode.value()).containsExactly("/recovery/send-code");
        assertThat(recover.value()).containsExactly("/recovery");
    }

    @Test
    void exposesRecoveryOperationsInTheServiceContract() {
        assertThat(methodNames(KeyManagementService.class))
                .contains("sendRecoveryCode", "recoverKey");
    }

    private static java.util.Set<String> methodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
    }
}
