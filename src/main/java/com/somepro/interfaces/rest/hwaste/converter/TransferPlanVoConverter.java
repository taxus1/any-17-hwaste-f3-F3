package com.somepro.interfaces.rest.hwaste.converter;

import com.somepro.domain.hwaste.model.PlanDetail;
import com.somepro.domain.hwaste.model.TransferPlan;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.hwaste.vo.PageVO;
import com.somepro.interfaces.rest.hwaste.vo.TransferPlanVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * TransferPlan / PlanDetail（领域）→ 对外 VO 转换器（用户接口层）。
 */
public final class TransferPlanVoConverter {

    private TransferPlanVoConverter() {
    }

    /** 领域聚合 → VO：列表场景没有在库总量，stockWeight 为 null。 */
    public static TransferPlanVO toVo(TransferPlan domain) {
        return new TransferPlanVO(
                domain.getId(),
                domain.getPlanNo(),
                domain.getSourceId(),
                domain.getCategoryCode(),
                domain.getPlanYear(),
                domain.getPlannedWeight(),
                domain.getApprovedWeight(),
                domain.getStatus() == null ? null : domain.getStatus().name(),
                null,
                domain.getCreateTime());
    }

    /** 详情视图 → VO：带上该单位该类别当前在库总量。 */
    public static TransferPlanVO toDetailVo(PlanDetail detail) {
        return new TransferPlanVO(
                detail.id(),
                detail.planNo(),
                detail.sourceId(),
                detail.categoryCode(),
                detail.planYear(),
                detail.plannedWeight(),
                detail.approvedWeight(),
                detail.status() == null ? null : detail.status().name(),
                detail.stockWeight(),
                detail.createTime());
    }

    public static PageVO<TransferPlanVO> toPageVo(PageResult<TransferPlan> page) {
        List<TransferPlanVO> content = page.content().stream()
                .map(TransferPlanVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
