package com.aseubel.yusi.pojo.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SecurityAuditEnumCompatibilityTest {

    @Test
    void keepsLegacyAuditValuesReadable() {
        assertDoesNotThrow(() -> SecurityAuditAction.valueOf("BACKUP_KEY_ACCESSED"));
        assertDoesNotThrow(() -> SecurityAuditOperation.valueOf("READ"));
        assertDoesNotThrow(() -> SecurityAuditResourceType.valueOf("USER_BACKUP_KEY"));
    }
}
