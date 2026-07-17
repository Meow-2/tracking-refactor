package com.wisdri.tracking.domain.model.config.batch;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批次跟踪中工艺侧与卷号点位的绑定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingPointGroup {
    /**
     * 对应的 segment 编码，例如 north、south。
     */
    private String segment;

    /**
     * 该侧的卷号点位。
     */
    private PointConfig coilNo;
}
