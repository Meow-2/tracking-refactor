package com.wisdri.tracking.domain.model.config.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 跟踪算法主配置。
 *
 * <p>定义速度点、长度模式、启动条件和用于选择钢卷的点位组。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingSection {
    /**
     * 跟踪点位前缀。
     */
    private String pointPrefix;

    /**
     * 速度点位配置。
     */
    private PointConfig speedPoint;

    /**
     * 跟踪启动条件；为空时表示不做启动阈值判断。
     */
    private StartCondition startCondition;

    /**
     * 长度计算模式。
     */
    private LengthMode lengthMode;

    /**
     * 轧机模式配置；仅 lengthMode 为 ROLLING 时生效。
     */
    private RollingConfig rolling;

    /**
     * 用于识别当前钢卷和长度的点位组。
     */
    private List<TrackingPointGroup> points;
}
