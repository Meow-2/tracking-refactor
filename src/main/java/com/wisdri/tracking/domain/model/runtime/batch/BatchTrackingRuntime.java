package com.wisdri.tracking.domain.model.runtime.batch;

import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个模板实例的批次跟踪运行态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchTrackingRuntime extends TrackingRuntime {
    /**
     * 最近一帧读取到的生产状态值。
     */
    private BigDecimal productionStatus;

    /**
     * 按 segment 编码索引的当前卷号，例如 north -> 卷号。
     */
    private Map<String, String> coilNos = new LinkedHashMap<>();

    /** 各工艺侧独立的卷身份和取号状态；旧运行态没有此字段时从 coilNos 兼容读取。 */
    private Map<String, BatchSegmentRuntime> segments = new LinkedHashMap<>();
}
