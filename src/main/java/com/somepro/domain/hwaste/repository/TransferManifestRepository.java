package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.TransferManifest;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * 电子转移联单仓储端口：领域层定义，基础设施层实现。
 *
 * 开单的「额度校验 + 编号分配 + 插入」要保证并发安全：同一计划几乎同时开两张联单，
 * 合计也不能盖过批复量；多个请求同时抢号，只该各拿各的号、互不相撞。
 * 由实现侧用同一把 EM 锁把「合计已开量 + 取号 + 插入」串起来，库表唯一约束兜底。
 * 审批 / 退回用条件更新落库：只 SUBMITTED 能流转，后到的请求更新 0 行即拒。
 */
public interface TransferManifestRepository {

    /**
     * 开单：在 EM 锁内先合计该计划当年已开联单量（退回 / 作废不占额度），
     * 与本单之和盖过 approvedWeight 就抛业务异常；没超再取号（EM-年份-序号）插入。
     */
    Mono<TransferManifest> create(TransferManifest manifest, BigDecimal approvedWeight);

    Mono<TransferManifest> findById(Long id);

    Mono<TransferManifest> findByManifestNo(String manifestNo);

    /** 审批：条件更新仅 SUBMITTED → APPROVED；重复审批更新 0 行。 */
    Mono<TransferManifest> approve(TransferManifest manifest);

    /** 退回：条件更新仅 SUBMITTED → REJECTED；重复退回更新 0 行。 */
    Mono<TransferManifest> reject(TransferManifest manifest);

    /** 多条件分页：计划 / 单位 / 类别 / 处置单位 / 状态均可选，一个都不传则分页列全；每行带联单号。 */
    Mono<PageResult<TransferManifest>> page(int pageNum, int pageSize, Long planId, Long sourceId,
                                            String categoryCode, Long unitId, String status);
}
