package com.wisdri.tracking.domain.model.runtime.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 单台候选设备的连续采样窗口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCandidateRuntime {
    private String coilNo;
    private String colorNo;
    /**
     * 当前钢卷在该设备上已采集到的最大长度。
     */
    private BigDecimal maxLength;
    private List<BigDecimal> lengths = new ArrayList<>();
}
