package com.aseubel.yusi.pojo.dto.admin;

import com.aseubel.yusi.pojo.constant.SecurityAuditAction;
import com.aseubel.yusi.pojo.constant.SecurityAuditOutcome;
import com.aseubel.yusi.pojo.constant.SecurityAuditResourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Low-sensitivity filters available to an authorized administrator. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditQuery {

    private SecurityAuditAction action;
    private SecurityAuditOutcome outcome;
    private SecurityAuditResourceType resourceType;
    private String userId;
}
