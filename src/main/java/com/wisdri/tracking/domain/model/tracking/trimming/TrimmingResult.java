package com.wisdri.tracking.domain.model.tracking.trimming;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 待写入 qm_trimming_log 的切边结果。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TrimmingResult extends TrackingResult {
    private String segmentCode;
    private String inMatNo;
    private Integer inMatNoProdNo;
    private BigDecimal headLength;
    private BigDecimal coilWidthPv;
    private BigDecimal coilWidthSv;
    private BigDecimal trimmingLength;
    /** 已有零切边记录需要更新为非零。 */
    private Boolean updateExisting;
}
