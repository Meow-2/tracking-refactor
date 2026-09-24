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
     * 是否反转方向点含义：false 时点值 false 为右往左，true 为左往右；
     * 配置 true 时交换上述含义，实际方向为方向点值与本字段异或。
     */
    private Boolean directReverse;
}
