package com.somepro.domain.hwaste.repository;

import com.somepro.domain.hwaste.model.TreatmentUnit;
import reactor.core.publisher.Mono;

/**
 * 处置利用单位仓储端口：领域层定义，基础设施层实现。开联单据此校验单位状态与可处置类别。
 */
public interface TreatmentUnitRepository {

    /** 按 id 查处置单位；不存在返回空。 */
    Mono<TreatmentUnit> findById(Long id);
}
