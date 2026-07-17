package com.wisdri.tracking.domain.model.config.process;

import com.wisdri.tracking.domain.model.config.PointConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一组用于确定当前钢卷的跟踪点位。
 *
 * <p>通常包含一个钢卷号点位和一个或多个长度点位。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingPointGroup {
    /**
     * 长度点位列表；焊缝模式下通常包含多个检测仪长度点位。
     */
    private List<PointConfig> length;

    /**
     * 钢卷号点位配置。
     */
    private PointConfig coilNo;

    /**
     * 轧机模式下用于区分卷取机侧和开卷机侧。
     */
    private Boolean isRollingCoiler;
}
