package com.somepro.domain.hwaste.model;

/**
 * 电子转移联单状态机（纯领域枚举，落库时存 name() 字符串）。
 *
 * 本模块驱动的流转：SUBMITTED → APPROVED；SUBMITTED 可退回 → REJECTED。
 * 新提交的联单一律落 SUBMITTED；只有 SUBMITTED 能审批 / 退回，
 * 已批过、退过、或已作废的联单不再来回审。
 * IN_TRANSIT / RECEIVED / DISPOSED 属后续运输签收环节的状态，此处仅作枚举占位；
 * VOID 为作废终态。
 */
public enum ManifestStatus {

    /** 已提交：新开联单的初始状态，等监管审批。 */
    SUBMITTED,
    /** 已审批：审批通过，可以启运。 */
    APPROVED,
    /** 已退回：审批退回，终态，不再流转。 */
    REJECTED,
    /** 运输中：已启运未运抵。 */
    IN_TRANSIT,
    /** 已签收：处置单位已收货。 */
    RECEIVED,
    /** 已处置：处置完成，终态。 */
    DISPOSED,
    /** 已作废：终态，不再占用计划额度。 */
    VOID
}
