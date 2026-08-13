package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import com.aseubel.yusi.pojo.entity.LifeGraphRelationEvidence;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LifeGraphRelationProvenanceTest {

    @Test
    void relationHasAnExplicitOriginAndEvidenceHasUserScopedSourceFields() throws Exception {
        assertEquals(LifeGraphRelation.Origin.MANUAL,
                LifeGraphRelation.builder().build().getOrigin());

        assertNotNull(LifeGraphRelationEvidence.class.getDeclaredField("userId"));
        assertNotNull(LifeGraphRelationEvidence.class.getDeclaredField("relationId"));
        assertNotNull(LifeGraphRelationEvidence.class.getDeclaredField("sourceType"));
        assertNotNull(LifeGraphRelationEvidence.class.getDeclaredField("sourceId"));
        assertNotNull(LifeGraphRelationEvidence.class.getDeclaredField("occurrenceCount"));

        assertEquals(2, Arrays.stream(LifeGraphRelationEvidence.class.getDeclaredFields())
                .filter(field -> field.getName().equals("updatedAt") || field.getName().equals("createdAt"))
                .count());
    }
}
