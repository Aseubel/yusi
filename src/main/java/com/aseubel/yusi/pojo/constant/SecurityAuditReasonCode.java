package com.aseubel.yusi.pojo.constant;

/** Stable categories for security audit outcomes; never contains user content. */
public final class SecurityAuditReasonCode {

    public static final String ADMIN_MUTATION = "ADMIN_MUTATION";
    public static final String SYSTEM_INITIALIZATION = "SYSTEM_INITIALIZATION";
    public static final String ADMIN_POLICY_DENIED = "ADMIN_POLICY_DENIED";
    public static final String TARGET_NOT_FOUND = "TARGET_NOT_FOUND";
    public static final String SENSITIVE_ACCESS = "SENSITIVE_ACCESS";

    private SecurityAuditReasonCode() {
    }
}
