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
    /** 当前物料所属加工单元；旧运行态无此字段时由数据库记录完成判重。 */
    private String cellCode;
    private String coilNo;
    private Integer repeatProdNo;
    private BigDecimal headLength;
    private BigDecimal coilWidthPv;
    private BigDecimal coilWidthSv;
    private BigDecimal trimmingLength;
}
