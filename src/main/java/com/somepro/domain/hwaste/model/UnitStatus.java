package com.somepro.domain.hwaste.model;

/**
 * 处置利用单位状态（纯领域枚举，落库时存 name() 字符串）。
 * 只有 ACTIVE 的单位能接新联单；停用 / 吊销的一律挡回。
 */
public enum UnitStatus {

    /** 正常：可以接收新转移联单。 */
    ACTIVE,
    /** 停用：暂停经营，接不了新货。 */
    SUSPENDED,
    /** 吊销：许可证被吊销，接不了新货。 */
    REVOKED
}
