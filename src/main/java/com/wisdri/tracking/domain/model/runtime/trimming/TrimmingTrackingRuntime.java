package com.wisdri.tracking.domain.model.runtime.trimming;

import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 切边跟踪算法的当前运行态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TrimmingTrackingRuntime extends TrackingRuntime {
    private BigDecimal speedPointValue;
    private BigDecimal startConditionPointValue;
    private Map<String, TrimmingSegmentRuntime> segments = new LinkedHashMap<>();
}
