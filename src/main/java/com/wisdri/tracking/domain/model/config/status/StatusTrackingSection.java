package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.StartCondition;
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
    private List<StatusPointGroup> points;
}
