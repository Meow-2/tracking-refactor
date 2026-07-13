package com.wisdri.tracking.domain.model.runtime.process;

import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 过程跟踪算法的当前运行态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessTrackingRuntime extends TrackingRuntime {
    /**
     * 按工艺段编码索引的当前状态。
     */
    private Map<String, ProcessSegmentRuntime> segments = new LinkedHashMap<>();
}
