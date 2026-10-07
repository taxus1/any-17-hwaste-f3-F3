package com.somepro.application.hwaste.port;

import reactor.core.publisher.Mono;

/**
 * 追加申报额度端口（应用层端口，基础设施层用响应式 Redis 实现）。
 *
 * 为什么要这个端口：t_transfer_plan 只有申报量 / 批复量 / 四档状态，没有字段能区分
 * 「当前 SUBMITTED 是首轮申报还是追加申报」，也记不住「这份计划当年追加已经批过一次」。
 * 这两件事放在 Redis：键存在即该计划有追加占用，done 标记追加是否已批。
 *
 * 语义：
 * - begin：键不存在才占上（原子 SETNX）；两笔追加几乎同时递，只该成一笔。
 * - find：看占用是「在途」还是「已批」；追加在途时批复额度不得放宽。
 * - finish：追加批复成功，置 done=true（追加额度已入账，当年不能再追加）。
 * - cancel：追加驳回，退回原状态，占用清除（这次追加不消耗当年资格，可重新发起）。
 */
public interface PlanAppendPort {

    /** 原子占坑：没有占用时写入并回 true；已在途 / 已批过时回 false。 */
    Mono<Boolean> begin(PlanAppendClaim claim);

    /** 查追加占用；没有则空。 */
    Mono<PlanAppendClaim> find(Long planId);

    /** 追加批复完成：置 done=true。 */
    Mono<Void> finish(Long planId);

    /** 追加驳回：清除占用，当年追加资格重新放出。 */
    Mono<Void> cancel(Long planId);
}
