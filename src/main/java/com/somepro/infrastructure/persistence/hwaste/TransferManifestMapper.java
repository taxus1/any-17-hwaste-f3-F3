package com.somepro.infrastructure.persistence.hwaste;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.somepro.infrastructure.persistence.hwaste.po.TransferManifestPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * t_transfer_manifest 的 MyBatis-Plus Mapper（基础设施层，阻塞 JDBC，只能在 boundedElastic 线程上调用）。
 *
 * 编号取数说明：MAX 查询故意不过滤 del_flag —— 已删除联单的编号也不许复用；
 * FOR UPDATE 走当前已提交数据，避免事务快照里读到旧的最大号。
 *
 * 额度合计说明：sumWeightByPlan 是手写 SQL，@TableLogic 不会自动拼 del_flag，
 * 这里显式写上 del_flag = 0；退回（REJECTED）与作废（VOID）的联单不占计划额度，不计入合计。
 */
@Mapper
public interface TransferManifestMapper extends BaseMapper<TransferManifestPO> {

    @Select("SELECT MAX(manifest_no) FROM t_transfer_manifest WHERE manifest_no LIKE CONCAT(#{prefix}, '%') FOR UPDATE")
    String maxManifestNo(@Param("prefix") String prefix);

    @Select("SELECT COALESCE(SUM(transfer_weight), 0) FROM t_transfer_manifest "
            + "WHERE plan_id = #{planId} AND del_flag = 0 "
            + "AND status IN ('SUBMITTED', 'APPROVED', 'IN_TRANSIT', 'RECEIVED', 'DISPOSED')")
    BigDecimal sumWeightByPlan(@Param("planId") Long planId);
}
