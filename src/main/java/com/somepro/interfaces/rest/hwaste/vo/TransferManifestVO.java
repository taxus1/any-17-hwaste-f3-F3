package com.somepro.interfaces.rest.hwaste.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电子转移联单对外返回对象（VO，用户接口层）—— 不可变 record。
 * 列表与详情共用；每行都带联单号 manifestNo。
 * delFlag / createBy / updateBy / updateTime 不进 API 契约。
 */
public record TransferManifestVO(
        Long id,
        String manifestNo,
        Long planId,
        Long sourceId,
        Long unitId,
        String categoryCode,
        String transporter,
        BigDecimal transferWeight,
        Integer crossProvince,
        String status,
        LocalDateTime createTime) implements Serializable {
}
