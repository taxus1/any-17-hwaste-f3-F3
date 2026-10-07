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
 * 提交的「额度复核 + 取号 + 插入」包在同一把 EM 锁里：同一计划下两笔几乎同时提交，
 * 后到的在锁里重算已开出量，额度不够就挡回去，不会一起把额度用穿；
 * 同一个联单号也只成一单，库表 uk_manifest_no 唯一约束是最后兜底。
 * 审批 / 退回走条件更新（UPDATE ... WHERE id=? AND status='SUBMITTED'），
 * 更新 0 行说明已被别人审过，后到的请求直接拒掉。
 */
@Repository
public class TransferManifestRepositoryImpl extends BaseBlockingRepository implements TransferManifestRepository {

    private final TransferManifestMapper transferManifestMapper;
    private final BizNoService bizNoService;

    public TransferManifestRepositoryImpl(TransferManifestMapper transferManifestMapper, BizNoService bizNoService) {
        this.transferManifestMapper = transferManifestMapper;
        this.bizNoService = bizNoService;
    }

    @Override
    public Mono<TransferManifest> create(TransferManifest manifest, BigDecimal approvedWeight) {
        return blocking(() -> bizNoService.inLock("EM", () -> {
            // 锁内复核额度：已开出量 + 本趟量不得盖过计划批复总量
            BigDecimal used = transferManifestMapper.sumTransferWeight(manifest.getPlanId());
            manifest.requireWithinQuota(approvedWeight, used);
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
        return blocking(() -> transition(manifest, ManifestStatus.APPROVED.name(),
                "联单已审批或已退回，不能重复审批"));
    }

    @Override
    public Mono<TransferManifest> reject(TransferManifest manifest) {
        return blocking(() -> transition(manifest, ManifestStatus.REJECTED.name(),
                "联单已审批或已退回，不能重复退回"));
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

    /** 通用状态条件更新：只有 SUBMITTED 能推到目标状态，更新 0 行即已被别人审过。 */
    private TransferManifest transition(TransferManifest manifest, String targetStatus, String conflictMsg) {
        TransferManifestPO patch = new TransferManifestPO();
        patch.setStatus(targetStatus);
        int rows = transferManifestMapper.update(patch, Wrappers.<TransferManifestPO>lambdaUpdate()
                .eq(TransferManifestPO::getId, manifest.getId())
                .eq(TransferManifestPO::getStatus, ManifestStatus.SUBMITTED.name()));
        if (rows == 0) {
            throw new BizException(conflictMsg);
        }
        return TransferManifestPoConverter.toDomain(transferManifestMapper.selectById(manifest.getId()));
    }
}
