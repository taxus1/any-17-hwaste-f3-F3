package com.somepro.domain.hwaste.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计划详情视图（领域值对象，不可变 record）：看一份计划时，
 * 把申报量、批复量和该单位该类别的当前在库总量一并带出来，三个数对得上账。
 *
 * 追加在途时申报量是「累计申报总量」，批复量仍是原批复额度（追加未批额度不放宽）；
 * 在库总量按在库批次实时合计，不做快照。
 */
public record PlanDetail(
        Long id,
        String planNo,
        Long sourceId,
        String categoryCode,
        Integer planYear,
        BigDecimal plannedWeight,
        BigDecimal approvedWeight,
        PlanStatus status,
        BigDecimal stockWeight,
        LocalDateTime createTime) {

    public static PlanDetail of(TransferPlan plan, BigDecimal stockWeight) {
        return new PlanDetail(
                plan.getId(),
                plan.getPlanNo(),
                plan.getSourceId(),
                plan.getCategoryCode(),
                plan.getPlanYear(),
                plan.getPlannedWeight(),
                plan.getApprovedWeight(),
                plan.getStatus(),
                stockWeight == null ? BigDecimal.ZERO : stockWeight,
                plan.getCreateTime());
    }
}
