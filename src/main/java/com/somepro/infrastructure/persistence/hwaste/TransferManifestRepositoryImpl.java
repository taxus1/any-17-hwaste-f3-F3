package com.somepro.infrastructure.persistence.hwaste;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.common.exception.BizException;
import com.somepro.domain.hwaste.model.ManifestStatus;
import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.hwaste.repository.TransferManifestRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.TransferManifestPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.TransferManifestPO;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 电子转移联单仓储适配器（基础设施层）。
 *
 * 并发约定：
 * - 开单的「额度合计校验 + 取号 + 插入」包在同一把 EM 锁里 —— 同一计划几乎同时开两张联单，
 *   合计也不能盖过批复量；两个请求同时抢号，只该各拿各的号。uk_manifest_no 唯一约束最后兜底。
 * - 审批 / 退回走条件更新：UPDATE ... WHERE id=? AND status='SUBMITTED'，
 *   更新 0 行说明状态已被别人推走（已批 / 已退 / 已作废），后到的请求直接拒掉。
 */
@Repository
public class TransferManifestRepositoryImpl extends BaseBlockingRepository implements TransferManifestRepository {

    private final TransferManifestMapper transferManifestMapper;
    private final BizNoService bizNoService;

    public TransferManifestRepositoryImpl(TransferManifestMapper transferManifestMapper,
                                          BizNoService bizNoService) {
        this.transferManifestMapper = transferManifestMapper;
        this.bizNoService = bizNoService;
    }

    @Override
    public Mono<TransferManifest> create(TransferManifest manifest, BigDecimal approvedWeight) {
        return blocking(() -> bizNoService.inLock("EM", () -> {
            // 额度闸：该计划当年已开联单量（退回 / 作废不占额度）+ 本单，不得盖过批复量
            BigDecimal used = transferManifestMapper.sumWeightByPlan(manifest.getPlanId());
            BigDecimal after = (used == null ? BigDecimal.ZERO : used).add(manifest.getTransferWeight());
            BigDecimal quota = approvedWeight == null ? BigDecimal.ZERO : approvedWeight;
            if (after.compareTo(quota) > 0) {
                throw new BizException("转移重量超出年度计划批复额度，剩余可转 "
                        + quota.subtract(used == null ? BigDecimal.ZERO : used) + " 千克");
            }
            TransferManifestPO po = TransferManifestPoConverter.toPo(manifest);
            po.setId(IdUtil.getSnowflakeNextId());
            po.setManifestNo(bizNoService.nextManifestNo());
            transferManifestMapper.insert(po);
            return TransferManifestPoConverter.toDomain(po);
        }));
    }

    @Override
    public Mono<TransferManifest> findById(Long id) {
        return blocking(() -> {
            TransferManifestPO po = transferManifestMapper.selectById(id);
            return po == null ? null : TransferManifestPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<TransferManifest> findByManifestNo(String manifestNo) {
        return blocking(() -> {
            TransferManifestPO po = transferManifestMapper.selectOne(Wrappers.<TransferManifestPO>lambdaQuery()
                    .eq(TransferManifestPO::getManifestNo, manifestNo));
            return po == null ? null : TransferManifestPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<TransferManifest> approve(TransferManifest manifest) {
        return blocking(() -> transition(manifest, ManifestStatus.APPROVED,
                "只有已提交的联单才能审批，请勿重复审批"));
    }

    @Override
    public Mono<TransferManifest> reject(TransferManifest manifest) {
        return blocking(() -> transition(manifest, ManifestStatus.REJECTED,
                "只有已提交的联单才能退回，请勿重复退回"));
    }

    @Override
    public Mono<PageResult<TransferManifest>> page(int pageNum, int pageSize, Long planId, Long sourceId,
                                                   String categoryCode, Long unitId, String status) {
        return this.<PageResult<TransferManifest>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<TransferManifestPO> wrapper = Wrappers.<TransferManifestPO>lambdaQuery()
                        .eq(planId != null, TransferManifestPO::getPlanId, planId)
                        .eq(sourceId != null, TransferManifestPO::getSourceId, sourceId)
                        .eq(categoryCode != null && !categoryCode.isBlank(),
                                TransferManifestPO::getCategoryCode, categoryCode)
                        .eq(unitId != null, TransferManifestPO::getUnitId, unitId)
                        .eq(status != null && !status.isBlank(), TransferManifestPO::getStatus, status)
                        .orderByDesc(TransferManifestPO::getId);
                List<TransferManifestPO> rows = transferManifestMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<TransferManifest> content = rows.stream()
                        .map(TransferManifestPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    /** 审批 / 退回共用：仅 SUBMITTED 能流转，更新 0 行即已被别人推走。 */
    private TransferManifest transition(TransferManifest manifest, ManifestStatus target, String conflictMsg) {
        TransferManifestPO patch = new TransferManifestPO();
        patch.setStatus(target.name());
        int rows = transferManifestMapper.update(patch, Wrappers.<TransferManifestPO>lambdaUpdate()
                .eq(TransferManifestPO::getId, manifest.getId())
                .eq(TransferManifestPO::getStatus, ManifestStatus.SUBMITTED.name()));
        if (rows == 0) {
            throw new BizException(conflictMsg);
        }
        return TransferManifestPoConverter.toDomain(transferManifestMapper.selectById(manifest.getId()));
    }
}
