package com.wisdri.tracking.domain.model.runtime.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单个工艺段的当前过程跟踪状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessSegmentRuntime {
    /**
     * 工艺段编码。
     */
    private String segmentCode;

    /**
     * 当前钢卷号。
     */
    private String coilNo;

    /**
     * 当前带头长度。
     */
    private BigDecimal headLength;
}
