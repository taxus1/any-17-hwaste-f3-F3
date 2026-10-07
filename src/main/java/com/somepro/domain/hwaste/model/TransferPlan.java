package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 年度转移计划（聚合根，纯领域，无框架注解）。
 *
 * 一张计划盯一家产废单位 + 一类危废 + 一个年度：同组合只准挂一份，重复申报先拒掉
 * （唯一性由仓储侧锁 + 查重兜底）。计划编号形如 TP-2026-0001，由仓储层分配。
 *
 * 不变量集中在这里：
 * - 申报量 / 追加量必须大于 0；申报与追加都不能盖过该单位该类别当前在库总量。
 * - 批复量必须落在 [0, 当前申报总量] 之间，超了不批。
 * - 状态只能顺着走：DRAFT 才能申报 → SUBMITTED；SUBMITTED 才能批复 / 驳回；
 *   APPROVED 除发起追加申报外不再来回动。
 * - 追加只准从 APPROVED 发起；追加量叠到申报总量上重新走申报 → 批复；
 *   追加批下来之前批复额度（approvedWeight）一点不放宽；追加驳回后申报量回退、状态回 APPROVED。
 */
@Getter
@Setter
public class TransferPlan extends BaseEntity {

    private Long id;

    /** 计划编号，全局唯一，形如 TP-2026-0001（由仓储层分配，编号里的年份取计划年度）。 */
    private String planNo;

    private Long sourceId;

    private String categoryCode;

    /** 计划年度（公历年份）。 */
    private Integer planYear;

    /** 申报转移重量：首轮申报量；有追加在途 / 已批时为「累计申报总量」。 */
    private BigDecimal plannedWeight;

    /** 批复转移重量：批下来的额度；追加未批前保持原批复量不动。 */
    private BigDecimal approvedWeight;

    private PlanStatus status;

    /** 工厂方法：立单落草稿。申报量在不在在库线内由申报环节再算，立草稿时只校入参与正数。 */
    public static TransferPlan create(Long sourceId, String categoryCode, Integer planYear,
                                      BigDecimal plannedWeight) {
        if (sourceId == null) {
            throw new BizException("产废单位不能为空");
        }
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new BizException("危废类别不能为空");
        }
        if (planYear == null || planYear < 2000 || planYear > 2099) {
            throw new BizException("计划年度不合法");
        }
        if (plannedWeight == null || plannedWeight.signum() <= 0) {
            throw new BizException("申报转移重量必须大于 0");
        }
        TransferPlan plan = new TransferPlan();
        plan.setSourceId(sourceId);
        plan.setCategoryCode(categoryCode.trim());
        plan.setPlanYear(planYear);
        plan.setPlannedWeight(plannedWeight);
        plan.setApprovedWeight(BigDecimal.ZERO);
        plan.setStatus(PlanStatus.DRAFT);
        return plan;
    }

    /**
     * 改量：只有草稿 / 首轮被驳回的单子能改申报量，改完重新走申报。
     * 追加轮回到 SUBMITTED / APPROVED 的单子不在此改（追加量在发起追加时一次性叠上）。
     */
    public void edit(BigDecimal plannedWeight) {
        require(status == PlanStatus.DRAFT || status == PlanStatus.REJECTED,
                "只有草稿或已驳回的计划才能修改申报量");
        if (plannedWeight == null || plannedWeight.signum() <= 0) {
            throw new BizException("申报转移重量必须大于 0");
        }
        this.plannedWeight = plannedWeight;
    }

    /** 首轮申报：草稿 / 驳回 → 已申报；申报量不能盖过该单位该类别当前在库总量。 */
    public void submit(BigDecimal stockWeight) {
        require(status == PlanStatus.DRAFT || status == PlanStatus.REJECTED,
                "只有草稿或已驳回的计划才能申报");
        requireWithinStock(plannedWeight, stockWeight);
        this.status = PlanStatus.SUBMITTED;
    }

    /**
     * 发起追加申报：已批复 → 已申报，追加量叠到原申报总量上。
     * 累计申报量（原申报 + 追加）不能盖过当前在库总量；批复额度此刻不动。
     */
    public void beginAppend(BigDecimal appendWeight, BigDecimal stockWeight) {
        require(status == PlanStatus.APPROVED, "只有已批复的计划才能发起追加申报");
        if (appendWeight == null || appendWeight.signum() <= 0) {
            throw new BizException("追加重量必须大于 0");
        }
        BigDecimal cumulative = plannedWeight.add(appendWeight);
        requireWithinStock(cumulative, stockWeight);
        this.plannedWeight = cumulative;
        this.status = PlanStatus.SUBMITTED;
    }

    /**
     * 批复：已申报 → 已批复，把批下来的重量落上。
     * approvedWeight 传本轮批复后的额度总量，必须落在 [原批复量, 当前申报总量] 之间：
     * 追加批复只能把额度往上批（不能借机收回原批复），且批多少都不能超过申报总量。
     */
    public void approve(BigDecimal approvedWeight, BigDecimal baseApprovedWeight) {
        require(status == PlanStatus.SUBMITTED, "只有已申报的计划才能批复");
        if (approvedWeight == null || approvedWeight.signum() < 0) {
            throw new BizException("批复重量不能为空且不能为负");
        }
        BigDecimal base = baseApprovedWeight == null ? BigDecimal.ZERO : baseApprovedWeight;
        if (approvedWeight.compareTo(base) < 0) {
            throw new BizException("批复重量不能少于此前已批复的重量");
        }
        if (approvedWeight.compareTo(plannedWeight) > 0) {
            throw new BizException("批复重量不能超过申报重量");
        }
        this.approvedWeight = approvedWeight;
        this.status = PlanStatus.APPROVED;
    }

    /** 首轮驳回：已申报 → 已驳回，必须写明理由；驳回后改了还能再报。 */
    public void reject(String reason) {
        require(status == PlanStatus.SUBMITTED, "只有已申报的计划才能驳回");
        requireReason(reason);
        this.status = PlanStatus.REJECTED;
    }

    /**
     * 追加驳回：已申报 → 回到已批复，申报总量回退到追加前（本次追加不算数，原批复额度原封不动）。
     * 理由必填。追加资格不被这次驳回消耗 —— 回 APPROVED 后当年还能重新发起一次追加。
     */
    public void rejectAppend(String reason, BigDecimal plannedWeightBeforeAppend) {
        require(status == PlanStatus.SUBMITTED, "只有已申报的计划才能驳回");
        requireReason(reason);
        this.plannedWeight = plannedWeightBeforeAppend;
        this.status = PlanStatus.APPROVED;
    }

    private static void requireWithinStock(BigDecimal planned, BigDecimal stockWeight) {
        BigDecimal stock = stockWeight == null ? BigDecimal.ZERO : stockWeight;
        if (planned.compareTo(stock) > 0) {
            throw new BizException("申报重量不能超过该单位该类别当前在库总量");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException("驳回必须写明理由");
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BizException(message);
        }
    }
}
