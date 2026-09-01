package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.StartCondition;
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
    @Builder.Default
    private Boolean monotonicityCheckEnabled = false;
    /**
     * 轧机动态侧别配置；未配置时 points.side 始终作为实际侧别。
     */
    private RollingConfig rolling;
    private CoilerMethodDefinitions coilerMethodDef;
    private List<StatusPointGroup> points;
}
