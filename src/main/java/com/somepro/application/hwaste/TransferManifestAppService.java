package com.somepro.application.hwaste;

import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.PlanStatus;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.hwaste.model.TransferPlan;
import com.somepro.domain.hwaste.repository.TransferManifestRepository;
import com.somepro.domain.hwaste.repository.TransferPlanRepository;
import com.somepro.domain.hwaste.repository.TreatmentUnitRepository;
import com.somepro.domain.hwaste.repository.WasteCategoryRepository;
import com.somepro.domain.hwaste.repository.WasteSourceRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 电子转移联单用例编排（应用层）：开单（提交）→ 审批 / 退回，以及详情与多条件翻页。
 *
 * 规则落在领域对象 {@link TransferManifest}，这里只做编排：
 * 找计划、加载单位 / 类别 / 处置单位、把领域规则与仓储落库串起来。
 *
 * 开单门槛（一道道都得过）：
 * 1. 对得上的年度计划：同单位 + 同类别 + 同年度，且已批复（也可直接按 planId 指定，同样要已批复）；
 * 2. 额度：本单 + 该计划当年已开联单量 ≤ 批复量（并发由仓储层 EM 锁内的合计校验兜底）；
 * 3. 处置单位 ACTIVE 且可处置该类别；
 * 4. 类别名录限制跨省的，供废与收货两头不同省就不放行。
 */
@Service
public class TransferManifestAppService {

    private final TransferManifestRepository transferManifestRepository;
    private final TransferPlanRepository transferPlanRepository;
    private final WasteSourceRepository wasteSourceRepository;
    private final WasteCategoryRepository wasteCategoryRepository;
    private final TreatmentUnitRepository treatmentUnitRepository;

    public TransferManifestAppService(TransferManifestRepository transferManifestRepository,
                                      TransferPlanRepository transferPlanRepository,
                                      WasteSourceRepository wasteSourceRepository,
                                      WasteCategoryRepository wasteCategoryRepository,
                                      TreatmentUnitRepository treatmentUnitRepository) {
        this.transferManifestRepository = transferManifestRepository;
        this.transferPlanRepository = transferPlanRepository;
        this.wasteSourceRepository = wasteSourceRepository;
        this.wasteCategoryRepository = wasteCategoryRepository;
        this.treatmentUnitRepository = treatmentUnitRepository;
    }

    /**
     * 开单（提交）：新单一律落已提交。计划二选一指定 —— 传 planId 直接挂，
     * 或传 单位 + 类别 + 年度 由系统找那份已批复的计划。
     */
    public Mono<TransferManifest> submit(Long planId, Long sourceId, String categoryCode, Integer planYear,
                                         Long unitId, String transporter, BigDecimal transferWeight) {
        return Mono.defer(() -> resolvePlan(planId, sourceId, categoryCode, planYear)
                .flatMap(plan -> wasteSourceRepository.findById(plan.getSourceId())
                        .switchIfEmpty(Mono.error(new BizException("产废单位不存在")))
                        .flatMap(source -> wasteCategoryRepository.findByCode(plan.getCategoryCode())
                                .switchIfEmpty(Mono.error(new BizException("危废类别不存在")))
                                .flatMap(category -> treatmentUnitRepository.findById(unitId)
                                        .switchIfEmpty(Mono.error(new BizException("处置单位不存在")))
                                        .flatMap(unit -> {
                                            TransferManifest manifest = TransferManifest.create(
                                                    plan.getId(), source, unit, category,
                                                    transporter, transferWeight);
                                            return transferManifestRepository.create(manifest,
                                                    plan.getApprovedWeight());
                                        })))));
    }

    /** 审批：已提交 → 已审批；已批 / 已退 / 已作废的单子由领域与条件更新双重挡回。 */
    public Mono<TransferManifest> approve(Long manifestId, String manifestNo) {
        return load(manifestId, manifestNo).flatMap(manifest -> {
            manifest.approve();
            return transferManifestRepository.approve(manifest);
        });
    }

    /** 退回：已提交 → 已退回，必须写明理由。 */
    public Mono<TransferManifest> reject(Long manifestId, String manifestNo, String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new BizException("退回必须写明理由"));
        }
        return load(manifestId, manifestNo).flatMap(manifest -> {
            manifest.reject(reason);
            return transferManifestRepository.reject(manifest);
        });
    }

    /** 详情：manifestId 或 manifestNo 传其一。 */
    public Mono<TransferManifest> detail(Long manifestId, String manifestNo) {
        return load(manifestId, manifestNo);
    }

    /** 翻联单：计划 / 单位 / 类别 / 处置单位 / 状态随意挑，一个都不填分页列全。 */
    public Mono<PageResult<TransferManifest>> page(int pageNum, int pageSize, Long planId, Long sourceId,
                                                   String categoryCode, Long unitId, String status) {
        return transferManifestRepository.page(pageNum, pageSize, planId, sourceId, categoryCode, unitId, status);
    }

    /**
     * 找那份对得上的计划：传了 planId 按计划查（仍要已批复）；
     * 没传则按 单位 + 类别 + 年度 找已批复的那份，找不到（草稿 / 未批 / 已驳回）都开不出联单。
     */
    private Mono<TransferPlan> resolvePlan(Long planId, Long sourceId, String categoryCode, Integer planYear) {
        if (planId != null) {
            return transferPlanRepository.findById(planId)
                    .switchIfEmpty(Mono.error(new BizException("转移计划不存在")))
                    .flatMap(plan -> plan.getStatus() == PlanStatus.APPROVED
                            ? Mono.just(plan)
                            : Mono.error(new BizException("年度计划尚未批复，不能开转移联单")));
        }
        if (sourceId == null) {
            return Mono.error(new BizException("产废单位不能为空"));
        }
        if (categoryCode == null || categoryCode.isBlank()) {
            return Mono.error(new BizException("危废类别不能为空"));
        }
        if (planYear == null) {
            return Mono.error(new BizException("计划年度不能为空"));
        }
        return transferPlanRepository.findApproved(sourceId, categoryCode.trim(), planYear)
                .switchIfEmpty(Mono.error(new BizException("该单位该类别该年度没有已批复的转移计划，不能开转移联单")));
    }

    /** 按 id 或编号加载联单；两个都不传或查不到都视为业务失败。 */
    private Mono<TransferManifest> load(Long manifestId, String manifestNo) {
        Mono<TransferManifest> found;
        if (manifestId != null) {
            found = transferManifestRepository.findById(manifestId);
        } else if (manifestNo != null && !manifestNo.isBlank()) {
            found = transferManifestRepository.findByManifestNo(manifestNo.trim());
        } else {
            return Mono.error(new BizException("manifestId 或 manifestNo 必传其一"));
        }
        return found.switchIfEmpty(Mono.error(new BizException("转移联单不存在")));
    }
}
