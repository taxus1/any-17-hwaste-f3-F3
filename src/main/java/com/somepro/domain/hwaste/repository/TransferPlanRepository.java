package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.TransferPlan;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 年度转移计划仓储端口：领域层定义，基础设施层实现。
 *
 * 立单的「同单位 + 同类别 + 同年度唯一 + 编号分配 + 插入」要保证并发安全，
 * 由实现侧用锁与查重兜底（同一组合几乎同时递两份，只该成一份）。
 * 各状态流转用条件更新落库：后到的请求更新 0 行即拒，保证重复申报 / 重复批复 / 并发追加不叠两次。
 */
public interface TransferPlanRepository {

    /**
     * 立单：同一单位 + 类别 + 年度只准挂一份未删除的计划，重复立单抛业务异常。
     * 编号（TP-年度-序号）由实现侧分配，序号按计划年度取号。
     */
    Mono<TransferPlan> create(TransferPlan plan);

    Mono<TransferPlan> findById(Long id);

    Mono<TransferPlan> findByPlanNo(String planNo);

    /** 按单位 + 类别 + 年度查已批复（APPROVED）的计划；没有已批复的返回空。 */
    Mono<TransferPlan> findApproved(Long sourceId, String categoryCode, Integer planYear);

    /** 草稿 / 驳回改量：只在 DRAFT / REJECTED 上生效，状态已变则更新 0 行。 */
    Mono<TransferPlan> updateDraft(TransferPlan plan);

    /** 首轮申报：条件更新仅 DRAFT / REJECTED → SUBMITTED；重复申报更新 0 行。 */
    Mono<TransferPlan> submit(TransferPlan plan);

    /** 追加申报：条件更新仅 APPROVED → SUBMITTED，并把追加量叠进申报总量；并发两笔只成一笔。 */
    Mono<TransferPlan> appendSubmit(TransferPlan plan);

    /** 批复：条件更新仅 SUBMITTED → APPROVED，落批复重量；重复批复更新 0 行。 */
    Mono<TransferPlan> approve(TransferPlan plan);

    /** 驳回：条件更新仅 SUBMITTED 落目标状态（首轮 REJECTED / 追加驳回回 APPROVED）。 */
    Mono<TransferPlan> reject(TransferPlan plan);

    /** 多条件分页：单位 / 类别 / 年度 / 状态均可选，一个都不传则分页列全；每行带计划编号。 */
    Mono<PageResult<TransferPlan>> page(int pageNum, int pageSize, Long sourceId, String categoryCode,
                                        Integer planYear, String status);
}
