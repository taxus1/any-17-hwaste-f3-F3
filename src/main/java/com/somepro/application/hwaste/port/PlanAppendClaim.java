package com.somepro.application.hwaste.port;

import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 追加申报占用信息（应用层值对象）。
 *
 * 表结构只有四档状态，没有地方记「追加轮」，故追加占用落在 Redis：
 * 一份计划同时最多一笔追加，键存在即占用。里面带着回退要用的数：
 * - appendWeight：本次追加量；
 * - plannedWeightBeforeAppend：追加前的申报总量（驳回时把叠上去的量退回）；
 * - approvedWeightBeforeAppend：追加前的批复额度（批复时下限，不能借机收回原额度）；
 * - done：追加是否已批复（已批的当年不许再发起追加）。
 *
 * 实现成普通不可变类而非 record：要经 GenericJackson2JsonRedisSerializer 存取 Redis，
 * 该序列化器按无参构造 + setter 还原对象，record 没有无参构造，读回来只会是 LinkedHashMap。
 */
@Getter
public class PlanAppendClaim implements Serializable {

    private Long planId;
    private BigDecimal appendWeight;
    private BigDecimal plannedWeightBeforeAppend;
    private BigDecimal approvedWeightBeforeAppend;
    private boolean done;

    public PlanAppendClaim() {
    }

    public PlanAppendClaim(Long planId, BigDecimal appendWeight, BigDecimal plannedWeightBeforeAppend,
                           BigDecimal approvedWeightBeforeAppend, boolean done) {
        this.planId = planId;
        this.appendWeight = appendWeight;
        this.plannedWeightBeforeAppend = plannedWeightBeforeAppend;
        this.approvedWeightBeforeAppend = approvedWeightBeforeAppend;
        this.done = done;
    }
}
