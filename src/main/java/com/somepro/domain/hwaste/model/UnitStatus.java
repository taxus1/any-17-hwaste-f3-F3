package com.somepro.domain.hwaste.model;

/**
 * 处置利用单位状态（纯领域枚举）。
 */
public enum UnitStatus {

    /** 正常：可以接收新联单。 */
    ACTIVE,
    /** 停用：暂时接不了新货。 */
    SUSPENDED,
    /** 吊销：资质被吊销，接不了新货。 */
    REVOKED
}
