package com.aseubel.yusi.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 集合名配置。默认值与历史硬编码一致，生产行为零变化；
 * benchmark 等隔离场景可通过配置覆盖为独立前缀集合。
 *
 * <p>仅以 {@code @ConfigurationProperties} 声明，注册统一走
 * {@link com.aseubel.yusi.config.ai.MilvusConfig} 的 {@code @EnableConfigurationProperties}；
 * 不能再加 {@code @Configuration}/@Component，否则组件扫描会多注册一个同类型 Bean 导致注入歧义。</p>
 */
@Data
@ConfigurationProperties(prefix = "yusi.milvus.collections")
public class MilvusCollectionProperties {

    /** 日记/对话 embedding 集合 */
    private String embedding = "yusi_embedding_collection";

    /** 中期记忆集合 */
    private String midTermMemory = "yusi_mid_term_memory";

    /** 匹配画像集合 */
    private String matchProfile = "yusi_match_profile";
}
