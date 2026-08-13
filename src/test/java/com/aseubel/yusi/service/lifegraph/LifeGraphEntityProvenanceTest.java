package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.LifeGraphEntityEvidence;
import com.aseubel.yusi.pojo.entity.LifeGraphRelation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LifeGraphEntityProvenanceTest {

    @Test
    void entityEvidenceAndSemanticDirectionAreFirstClassContracts() throws Exception {
        assertNotNull(LifeGraphEntityEvidence.class.getDeclaredField("userId"));
        assertNotNull(LifeGraphEntityEvidence.class.getDeclaredField("entityId"));
        assertNotNull(LifeGraphEntityEvidence.class.getDeclaredField("sourceType"));
        assertNotNull(LifeGraphEntityEvidence.class.getDeclaredField("sourceId"));
        assertNotNull(LifeGraphEntityEvidence.class.getDeclaredField("evidenceKind"));
        assertNotNull(LifeGraphEntityEvidence.class.getDeclaredField("snippet"));
        assertNotNull(LifeGraphEntity.class.getDeclaredField("importance"));
        assertNotNull(LifeGraphRelation.class.getDeclaredField("semanticSourceId"));
        assertNotNull(LifeGraphRelation.class.getDeclaredField("semanticTargetId"));
        assertEquals(LifeGraphEntity.EntityType.Work,
                LifeGraphEntity.EntityType.valueOf("Work"));
    }
}
