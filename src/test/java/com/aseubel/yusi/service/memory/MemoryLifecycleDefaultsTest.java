package com.aseubel.yusi.service.memory;

import com.aseubel.yusi.pojo.entity.LifeGraphEntity;
import com.aseubel.yusi.pojo.entity.UserPersona;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemoryLifecycleDefaultsTest {

    @Test
    void userPersonaBuilderAppliesLifecycleDefaults() {
        UserPersona persona = UserPersona.builder().build();

        assertEquals("UNKNOWN", persona.getSourceType());
        assertNull(persona.getSourceId());
        assertEquals(0.5, persona.getConfidence());
        assertFalse(persona.getMatchAllowed());
        assertFalse(persona.getHidden());
        assertNull(persona.getValidUntil());
    }

    @Test
    void lifeGraphEntityBuilderAppliesLifecycleDefaults() {
        LifeGraphEntity entity = LifeGraphEntity.builder().build();

        assertEquals(0.5, entity.getConfidence());
        assertFalse(entity.getMatchAllowed());
        assertFalse(entity.getHidden());
        assertNull(entity.getValidUntil());
    }
}
