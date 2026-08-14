package com.aseubel.yusi.pojo.dto.key;

import lombok.Builder;
import lombok.Data;

/**
 * Old client key encrypted for the browser's one-time recovery key pair.
 */
@Data
@Builder
public class KeyRecoveryResponse {

    private String encryptedKey;

    private String keySalt;
}
