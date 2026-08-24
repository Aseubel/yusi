package com.aseubel.yusi.config;

import com.aseubel.yusi.common.web.DynamicCorsConfigurationSource;
import com.aseubel.yusi.service.web.RuntimeAccessPolicyService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CorsFilter;

/** CORS filter backed by the runtime access policy. */
@Configuration
public class WebCorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> runtimeCorsFilter(RuntimeAccessPolicyService policyService) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorsFilter(new DynamicCorsConfigurationSource(policyService::getEffectivePolicy)));
        registration.setName("runtimeCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
