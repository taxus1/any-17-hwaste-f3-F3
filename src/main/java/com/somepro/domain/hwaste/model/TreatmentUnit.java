package com.somepro.domain.hwaste.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 处置利用单位（纯领域对象）。开联单只关心它状态是否正常、能接哪些类别、
 * 落在哪个省，因此这里只承载联单校验用到的字段。
 */
@Getter
@Setter
public class TreatmentUnit {

    private Long id;

    /** 处置单位编号，形如 TU-2026-0001。 */
    private String unitNo;

    private String name;

    /** 所在省份（跨省判定用）。 */
    private String province;

    /** 可处置类别代码，逗号分隔（如 HW08,HW09）。 */
    private String disposes;

    private UnitStatus status;

    /** 只有正常（ACTIVE）的单位才接得了新货；停用、吊销都挡回去。 */
    public boolean isActive() {
        return this.status == UnitStatus.ACTIVE;
    }

    /** 这趟货的类别得落在对方能接的范围里。 */
    public boolean canAccept(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()
                || disposes == null || disposes.isBlank()) {
            return false;
        }
        for (String code : disposes.split(",")) {
            if (categoryCode.trim().equalsIgnoreCase(code.trim())) {
                return true;
            }
        }
        return false;
    }
}
