package com.aseubel.yusi.config;

import com.aseubel.yusi.common.jackson.RestPage;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;

/**
 * Jackson 配置
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册 Page 接口的反序列化实现
     */
    @Bean
    public com.fasterxml.jackson.databind.Module customPageModule() {
        SimpleModule module = new SimpleModule();
        module.addAbstractTypeMapping(Page.class, RestPage.class);
        return module;
    }
}
