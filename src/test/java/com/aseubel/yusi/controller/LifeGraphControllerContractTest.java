package com.aseubel.yusi.controller;

import com.aseubel.yusi.service.lifegraph.CommunityInsightService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class LifeGraphControllerContractTest {

    @Test
    void doesNotExposeLegacyCommunityDetailEndpoint() {
        assertThat(Arrays.stream(LifeGraphController.class.getDeclaredMethods())
                .map(Method::getName)
                .filter("getCommunityDetail"::equals)
                .findAny())
                .isEmpty();
    }

    @Test
    void communityInsightServiceDoesNotRequireLegacyDetailLookup() {
        assertThat(Arrays.stream(CommunityInsightService.class.getDeclaredMethods())
                .map(Method::getName)
                .filter("getCommunityDetail"::equals)
                .findAny())
                .isEmpty();
    }
}
