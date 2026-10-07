package com.somepro.domain.hwaste.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 电子转移联单（聚合根，纯领域，无框架注解）。
 *
 * 一张联单盯一趟货：挂在哪份年度计划下、哪家产废单位出货、哪家处置单位收货、
 * 哪类危废、谁拉、拉多少。联单编号形如 EM-2026-0001，由仓储层分配，全局唯一。
 *
 * 不变量集中在这里：
 * - 申报转移重量必须大于 0。
 * - 处置单位必须 ACTIVE（停用 / 吊销接不了新货），且这趟货的类别得落在对方可处置范围里。
 * - 类别名录标了限制跨省的，供废与收货两头不在同一个省就不许开单；
 *   是否跨省在提交时按两头省份快照到 crossProvince 上，之后不随名录或单位变化改写。
 * - 状态只能顺着走：新单落 SUBMITTED；SUBMITTED 才能审批 → APPROVED 或退回 → REJECTED；
 *   已批过、退过、作废的联单不再来回审。
 *
 * 计划额度（本单 + 该计划当年已开联单量 ≤ 批复量）涉及并发合计，
 * 由仓储层在取号锁内校验兜底，不在这里做。
 */
@Getter
@Setter
public class TransferManifest extends BaseEntity {

    private Long id;

    /** 联单编号，全局唯一，形如 EM-2026-0001（由仓储层分配，年份取开单自然年）。 */
    private String manifestNo;

    /** 挂在哪份年度计划下（t_transfer_plan.id）。 */
    private Long planId;

    private Long sourceId;

    /** 处置单位 id（t_treatment_unit.id）。 */
    private Long unitId;

    private String categoryCode;

    /** 运输单位名称（这趟货归谁拉）。 */
    private String transporter;

    /** 这趟申报转移重量（千克）。 */
    private BigDecimal transferWeight;

    /** 是否跨省：提交时按供废 / 收货两头省份快照，1 是 / 0 否。 */
    private Integer crossProvince;

    private ManifestStatus status;

    /**
     * 工厂方法：开单落已提交。计划匹配与额度校验在应用层 / 仓储层完成，
     * 这里把处置单位与跨省这两道门槛守住。
     */
    public static TransferManifest create(Long planId, WasteSource source, TreatmentUnit unit,
                                          WasteCategory category, String transporter,
                                          BigDecimal transferWeight) {
        if (planId == null) {
            throw new BizException("年度计划不能为空");
        }
        if (source == null || source.getId() == null) {
            throw new BizException("产废单位不能为空");
        }
        if (unit == null || unit.getId() == null) {
            throw new BizException("处置单位不能为空");
        }
        if (category == null || category.getCategoryCode() == null || category.getCategoryCode().isBlank()) {
            throw new BizException("危废类别不能为空");
        }
        if (transferWeight == null || transferWeight.signum() <= 0) {
            throw new BizException("申报转移重量必须大于 0");
        }
        if (!unit.isActive()) {
            throw new BizException("处置单位已停用或吊销，不能接收新联单");
        }
        if (!unit.canAccept(category.getCategoryCode())) {
            throw new BizException("处置单位不能处置该危废类别");
        }
        boolean cross = !Objects.equals(source.getProvince(), unit.getProvince());
        if (cross && category.isCrossProvinceRestricted()) {
            throw new BizException("该危废类别限制跨省转移，供废与收货单位不在同一省份，不能开联单");
        }
        TransferManifest manifest = new TransferManifest();
        manifest.setPlanId(planId);
        manifest.setSourceId(source.getId());
        manifest.setUnitId(unit.getId());
        manifest.setCategoryCode(category.getCategoryCode());
        manifest.setTransporter(transporter == null || transporter.isBlank() ? null : transporter.trim());
        manifest.setTransferWeight(transferWeight);
        manifest.setCrossProvince(cross ? 1 : 0);
        manifest.setStatus(ManifestStatus.SUBMITTED);
        return manifest;
    }

    /** 审批：已提交 → 已审批。 */
    public void approve() {
        require(status == ManifestStatus.SUBMITTED, "只有已提交的联单才能审批");
        this.status = ManifestStatus.APPROVED;
    }

    /** 退回：已提交 → 已退回，必须写明理由。 */
    public void reject(String reason) {
        require(status == ManifestStatus.SUBMITTED, "只有已提交的联单才能退回");
        if (reason == null || reason.isBlank()) {
            throw new BizException("退回必须写明理由");
        }
        this.status = ManifestStatus.REJECTED;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BizException(message);
        }
    }
}
