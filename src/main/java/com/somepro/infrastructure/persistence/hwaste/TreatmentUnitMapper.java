package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.TreatmentUnitPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * t_treatment_unit 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 */
@Mapper
public interface TreatmentUnitMapper extends BaseMapper<TreatmentUnitPO> {
}
