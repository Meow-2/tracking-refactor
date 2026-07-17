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
     * 当前运行态所属的模板实例编码，例如 fb1。
     */
    private String templateCode;

    /**
     * 最近一帧读取到的生产状态值。
     */
    private BigDecimal productionStatus;

    /**
     * 按 segment 编码索引的当前卷号，例如 north -> 卷号。
     */
    private Map<String, String> coilNos = new LinkedHashMap<>();
}
