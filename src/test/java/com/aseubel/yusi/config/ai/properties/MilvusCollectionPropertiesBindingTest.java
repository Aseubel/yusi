package com.aseubel.yusi.config.ai.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 集合名配置绑定：默认值与历史硬编码一致，benchmark 可整体覆盖为隔离前缀。 */
class MilvusCollectionPropertiesBindingTest {

    @Test
    void defaultsMatchLegacyHardcodedCollections() {
        MilvusCollectionProperties properties = new MilvusCollectionProperties();

        assertThat(properties.getEmbedding()).isEqualTo("yusi_embedding_collection");
        assertThat(properties.getMidTermMemory()).isEqualTo("yusi_mid_term_memory");
        assertThat(properties.getMatchProfile()).isEqualTo("yusi_match_profile");
    }

    @Test
    void benchmarkPrefixOverridesAllThreeCollections() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "yusi.milvus.collections.embedding", "yusi_benchmark_embedding_collection",
                "yusi.milvus.collections.mid-term-memory", "yusi_benchmark_mid_term_memory",
                "yusi.milvus.collections.match-profile", "yusi_benchmark_match_profile")));

        MilvusCollectionProperties bound = binder.bind("yusi.milvus.collections",
                Bindable.of(MilvusCollectionProperties.class)).get();

        assertThat(bound.getEmbedding()).isEqualTo("yusi_benchmark_embedding_collection");
        assertThat(bound.getMidTermMemory()).isEqualTo("yusi_benchmark_mid_term_memory");
        assertThat(bound.getMatchProfile()).isEqualTo("yusi_benchmark_match_profile");
    }
}
