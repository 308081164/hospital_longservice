package com.hospital.backend.security;

/**
 * NFR-04：特色账单权限角色常量。
 */
public final class BillingRoles {

    public static final String CONFIG = "R_BILLING_CONFIG";
    public static final String OPERATOR = "R_BILLING_OPERATOR";
    public static final String REVIEWER = "R_BILLING_REVIEWER";

    /** 对账任务审核：特色账单角色 + 普通登录用户（不含匿名） */
    public static final String RECONCILIATION_REVIEW =
            "SUPER, R_SUPER, R_ADMIN, R_BILLING_REVIEWER, R_BILLING_OPERATOR, R_BILLING_CONFIG, R_USER";

    private BillingRoles() {}
}
