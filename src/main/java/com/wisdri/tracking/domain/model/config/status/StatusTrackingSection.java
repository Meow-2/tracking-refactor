package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.process.RollingConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 状态跟踪算法配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusTrackingSection {
    private String pointPrefix;
    private StartCondition startCondition;
    private Integer sampleCount;
    private BigDecimal minLengthChange;

    /**
     * 当前设备未被识别时允许保留上一状态的最大计数；计数从 1 开始，超过该值后清空状态。
     */
    @Builder.Default
    private Integer currentClearThreshold = 1;

    @Builder.Default
    private Boolean monotonicityCheckEnabled = false;
    /**
     * 轧机动态侧别配置；未配置时 points.side 始终作为实际侧别。
     */
    private RollingConfig rolling;
    private CoilerMethodDefinitions coilerMethodDef;
    private List<StatusPointGroup> points;

    /**
     * 需延长生产次数缓存生命周期的产线卷号点位；按 pointPrefix 解析。
     * null 或空列表表示不启用扩展缓存，设备卷号仍由 points 提供。
     */
    private List<PointConfig> cachePoints;
}
