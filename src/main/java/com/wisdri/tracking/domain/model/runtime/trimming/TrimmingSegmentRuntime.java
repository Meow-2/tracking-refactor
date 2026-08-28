package com.wisdri.tracking.domain.model.runtime.trimming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 圆盘剪当前物料的切边运行态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrimmingSegmentRuntime {
    private String segmentCode;
    private String coilNo;
    private Integer productNo;
    private BigDecimal headLength;
    private BigDecimal coilWidthPv;
    private BigDecimal coilWidthSv;
    private BigDecimal trimmingLength;
}
