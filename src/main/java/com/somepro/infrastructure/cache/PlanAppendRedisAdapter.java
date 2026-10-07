package com.somepro.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.somepro.application.hwaste.port.PlanAppendClaim;
import com.somepro.application.hwaste.port.PlanAppendPort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 追加申报占用端口的 Redis 适配器（基础设施层）。
 *
 * 一份计划一个 key（hwaste:plan:append:{planId}），value 是 JSON（PlanAppendClaim）。
 * 键存在即占用：begin 用 SETNX 原子占坑，两笔追加几乎同时递只成一笔；
 * done=true 表示追加已批复（当年不能再追加）；驳回则删 key，把当年资格放回去。
 *
 * 反序列化说明：容器里的 ReactiveRedisTemplate 用 GenericJackson2JsonRedisSerializer 且
 * 未开启 default typing（写入不带 @class），读回无类型 JSON 是 LinkedHashMap，
 * 因此这里读出后统一用 ObjectMapper 显式转成 PlanAppendClaim，不能直接 cast。
 */
@Component
public class PlanAppendRedisAdapter implements PlanAppendPort {

    private static final String KEY_PREFIX = "hwaste:plan:append:";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public PlanAppendRedisAdapter(ReactiveRedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Boolean> begin(PlanAppendClaim claim) {
        return redisTemplate.opsForValue().setIfAbsent(key(claim.getPlanId()), claim);
    }

    @Override
    public Mono<PlanAppendClaim> find(Long planId) {
        return redisTemplate.opsForValue().get(key(planId))
                .map(raw -> raw instanceof PlanAppendClaim claim
                        ? claim
                        : objectMapper.convertValue(raw, PlanAppendClaim.class));
    }

    @Override
    public Mono<Void> finish(Long planId) {
        // 不删 key：已批的追加要一直占着，挡住当年第二次发起追加
        return find(planId)
                .flatMap(claim -> redisTemplate.opsForValue()
                        .set(key(planId), new PlanAppendClaim(
                                claim.getPlanId(),
                                claim.getAppendWeight(),
                                claim.getPlannedWeightBeforeAppend(),
                                claim.getApprovedWeightBeforeAppend(),
                                true)))
                .then();
    }

    @Override
    public Mono<Void> cancel(Long planId) {
        return redisTemplate.delete(key(planId)).then();
    }

    private static String key(Long planId) {
        return KEY_PREFIX + planId;
    }
}
