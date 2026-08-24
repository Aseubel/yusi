package com.aseubel.yusi.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebAccessPolicyFilterTest {

    @Test
    void blocksBusinessRequestsFromARejectedIpBeforeAuthentication() throws ServletException, IOException {
        RuntimeAccessPolicySnapshot policy = new RuntimeAccessPolicySnapshot(
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("203.0.113.0/24"),
                List.of(),
                1L,
                LocalDateTime.now());
        WebAccessPolicyFilter filter = new WebAccessPolicyFilter(() -> policy, new ClientIpResolver(""));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/me");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void leavesActuatorHealthChecksOutsideBusinessIpFiltering() throws ServletException, IOException {
        RuntimeAccessPolicySnapshot policy = new RuntimeAccessPolicySnapshot(
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of("203.0.113.0/24"),
                List.of(),
                1L,
                LocalDateTime.now());
        WebAccessPolicyFilter filter = new WebAccessPolicyFilter(() -> policy, new ClientIpResolver(""));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
