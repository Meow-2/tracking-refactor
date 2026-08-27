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
    /**
     * 当前帧的卷号和剩余长度是否都有效。
     * false 时设备仍保留在 candidates 中，但不得参与 current 选择或剪切计算。
     * 该字段允许为 null，以兼容变更前已写入 Redis 的 status runtime。
     */
    private Boolean dataComplete;

    private String coilNo;
    /**
     * 当前钢卷重复生产次数。
     */
    private Integer productNo;
    private String colorNo;
    /**
     * 当前钢卷固化的开卷卷取方式代码。
     */
    private String coilerMethod;
    /**
     * 当前钢卷固化的开卷卷取方式名称。
     */
    private String coilerMethodName;
    /**
     * 当前钢卷在该设备上已采集到的最大长度。
     */
    private BigDecimal maxLength;
    private List<BigDecimal> lengths = new ArrayList<>();
}
