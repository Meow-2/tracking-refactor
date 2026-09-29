package com.wisdri.tracking.domain.model.config.process;

import com.wisdri.tracking.domain.model.config.PointConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工艺段配置。
 *
 * <p>每个工艺段会根据当前跟踪配置生成一条跟踪结果记录。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentConfig {
    /**
     * 工艺段编码，用于表名、结果标识等稳定业务标识。
     */
    private String code;

    /**
     * 工艺段名称，用于展示。
     */
    private String name;

    /**
     * 工艺参数点位前缀。
     */
    private String pointPrefix;

    /**
     * 长度修正值，会参与带头长度计算。
     */
    private BigDecimal lengthCorrect;

    /**
     * 焊缝模式下选择长度数组的下标。
     */
    private Integer lengthArrayIndex;

    /**
     * 加工单元固定序号，范围 0～999；未配置固定值和点位时使用 1。
     */
    private Integer cellCodeValue;

    /**
     * 加工单元序号点位；配置后按本段点位前缀读取，缺值时不使用默认序号。
     */
    private PointConfig cellCodePoint;

    /**
     * 该工艺段需要采集的参数点位列表。
     */
    private List<PointConfig> points;
}
