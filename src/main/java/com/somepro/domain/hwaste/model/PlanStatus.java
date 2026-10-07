package com.somepro.domain.hwaste.model;

/**
 * 年度转移计划状态机（纯领域枚举，落库时存 name() 字符串）。
 *
 * 流转（首轮申报）：DRAFT → SUBMITTED → APPROVED；SUBMITTED 可驳回 → REJECTED，
 * 驳回后改了还能再报：REJECTED →（改量）→ SUBMITTED。
 *
 * 追加申报复用同一张单：APPROVED → SUBMITTED → APPROVED；追加被驳回则回到 APPROVED
 * （驳回只代表本次追加没成，原批复额度仍在，当年可重新发起一次追加）。
 * APPROVED 计划本身不再改动；追加额度在批下来之前（SUBMITTED）不得计入批复额度。
 */
public enum PlanStatus {

    /** 草稿：新起立单，可改可删，尚未申报。 */
    DRAFT,
    /** 已申报：等监管批复；首轮申报与追加申报都停在这一档。 */
    SUBMITTED,
    /** 已批复：批复重量已落账；当年可再发起一次追加申报。 */
    APPROVED,
    /** 已驳回：首轮申报被驳回，改量后可重新申报。 */
    REJECTED
}
