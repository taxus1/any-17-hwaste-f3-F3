package com.somepro.domain.hwaste.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

/**
 * 处置利用单位（纯领域对象）。开联单只关心它能不能接这趟货：
 * 状态是否 ACTIVE、可处置类别范围里有没有这趟货的类别、以及所在省份（跨省判定用），
 * 因此这里只承载这些字段。
 */
@Getter
@Setter
public class TreatmentUnit {

    private Long id;

    /** 处置单位编号，形如 TU-2026-0001。 */
    private String unitNo;

    private String name;

    /** 所在省份，跨省转移判定用。 */
    private String province;

    /** 可处置类别代码，逗号分隔（如 HW08,HW09）。 */
    private String disposes;

    private UnitStatus status;

    /** 只有正常（ACTIVE）的单位才允许接收新联单。 */
    public boolean isActive() {
        return this.status == UnitStatus.ACTIVE;
    }

    /** 可处置类别名录里有没有这个类别；名录为空表示什么都接不了。 */
    public boolean canAccept(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()
                || this.disposes == null || this.disposes.isBlank()) {
            return false;
        }
        return Arrays.stream(this.disposes.split(","))
                .map(String::trim)
                .anyMatch(code -> code.equals(categoryCode));
    }
}
