package com.aseubel.yusi.config.ai;

import com.aseubel.yusi.config.ai.properties.MilvusCollectionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * {@link MilvusCollectionProperties} 的唯一注册点。
 *
 * <p>不放在 {@link MilvusConfig}（{@code @Profile("!test")}）里：中期记忆等服务
 * 在 test profile 下也会被加载并依赖该 properties，test 上下文需要它注册。</p>
 */
@Configuration
@EnableConfigurationProperties(MilvusCollectionProperties.class)
public class MilvusCollectionConfig {
}
