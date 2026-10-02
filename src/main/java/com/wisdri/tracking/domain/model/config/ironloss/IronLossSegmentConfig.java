package com.wisdri.tracking.domain.model.config.ironloss;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.Data;

import java.util.List;

/** 铁损工艺段配置；只承载采集和加工单元代码所需字段。 */
@Data
public class IronLossSegmentConfig {
    /** 工艺段稳定编码，用于结果标识和时序表名。 */
    private String code;

    /** 工艺段显示名称，不参与点位读取和建表。 */
    private String name;

    /** 本段参数及可选加工单元序号点位的路径前缀。 */
    private String pointPrefix;

    /** 固定加工单元序号，范围 0～999；无固定值与点位时默认为 1。 */
    private Integer cellCodeValue;

    /** 加工单元序号点位；配置后缺值或无效值不回退到固定值。 */
    private PointConfig cellCodePoint;

    /** Cube 直属参数点位，按目录顺序建列并采样；同名 cell_code 只作固定列来源。 */
    private List<PointConfig> points;
}
