package com.aseubel.yusi.config.ai.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingModelConfigPropertiesTest {

    @Test
    void usesConfiguredEmbeddingDimensionWithoutModelProbe() {
        EmbeddingModelConfigProperties properties = new EmbeddingModelConfigProperties();

        assertEquals(1024, properties.getDimension());

        properties.setDimension(1536);

        assertEquals(1536, properties.getDimension());
    }
}
