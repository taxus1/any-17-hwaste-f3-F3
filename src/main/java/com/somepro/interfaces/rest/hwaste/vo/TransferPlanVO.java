package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 年度转移计划对外返回对象（VO，用户接口层）—— 不可变 record。
 * 列表与详情共用；详情时 stockWeight 带该单位该类别当前在库总量，列表不要求时为 null。
 * delFlag / createBy / updateBy / updateTime 不进 API 契约。
 */
public record TransferPlanVO(
        Long id,
        String planNo,
        Long sourceId,
        String categoryCode,
        Integer planYear,
        BigDecimal plannedWeight,
        BigDecimal approvedWeight,
        String status,
        BigDecimal stockWeight,
        LocalDateTime createTime) implements Serializable {
}
