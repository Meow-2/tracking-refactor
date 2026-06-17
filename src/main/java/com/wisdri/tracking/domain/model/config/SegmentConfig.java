package com.wisdri.tracking.domain.model.config;

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
     * 工艺段名称。
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
     * 该工艺段需要采集的参数点位短名列表。
     */
    private List<String> points;
}
