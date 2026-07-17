package com.wisdri.tracking.domain.model.config.process;

import com.wisdri.tracking.domain.model.config.PointConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轧机模式专用配置。
 *
 * <p>用于根据轧制方向和道次号判断当前应选择入口卷还是出口卷。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollingConfig {
    /**
     * 轧制方向点位配置。
     */
    private PointConfig directPoint;

    /**
     * 道次号点位配置。
     */
    private PointConfig passNoPoint;

    /**
     * 是否反转轧制方向判断结果。
     */
    private Boolean directReverse;
}
