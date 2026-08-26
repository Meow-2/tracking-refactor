package com.wisdri.tracking.domain.model.runtime.shear;

import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通用剪切跟踪运行态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShearTrackingRuntime extends TrackingRuntime {
    /**
     * 各剪刀的计数运行态；键为 ShearPointConfig.name，不在不同剪刀间共享刀次。
     */
    @lombok.Builder.Default
    private Map<String, ShearPointRuntime> points = new LinkedHashMap<>();
}
