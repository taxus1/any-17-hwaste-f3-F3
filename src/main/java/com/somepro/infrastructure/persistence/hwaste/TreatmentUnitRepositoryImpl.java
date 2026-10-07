package com.somepro.infrastructure.persistence.hwaste;

import com.somepro.domain.hwaste.model.TreatmentUnit;
import com.somepro.domain.hwaste.repository.TreatmentUnitRepository;
import com.somepro.infrastructure.persistence.base.BaseBlockingRepository;
import com.somepro.infrastructure.persistence.hwaste.converter.TreatmentUnitPoConverter;
import com.somepro.infrastructure.persistence.hwaste.po.TreatmentUnitPO;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 处置利用单位仓储适配器（基础设施层）。只读 lookup，供开联单校验单位状态与可处置类别。
 */
@Repository
public class TreatmentUnitRepositoryImpl extends BaseBlockingRepository implements TreatmentUnitRepository {

    private final TreatmentUnitMapper treatmentUnitMapper;

    public TreatmentUnitRepositoryImpl(TreatmentUnitMapper treatmentUnitMapper) {
        this.treatmentUnitMapper = treatmentUnitMapper;
    }

    @Override
    public Mono<TreatmentUnit> findById(Long id) {
        return blocking(() -> {
            TreatmentUnitPO po = treatmentUnitMapper.selectById(id);
            return po == null ? null : TreatmentUnitPoConverter.toDomain(po);
        });
    }
}
