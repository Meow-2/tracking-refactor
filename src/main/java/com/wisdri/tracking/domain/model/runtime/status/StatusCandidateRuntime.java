package com.wisdri.tracking.domain.model.runtime.status;

import com.fasterxml.jackson.annotation.JsonAlias;
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
     * 候选设备代码，与 StatusTrackingRuntime.candidates 的键保持一致。
     */
    private String deviceCode;

    /**
     * 候选设备名称，随状态快照固化，避免消费侧重新读取配置。
     */
    private String deviceName;

    /**
     * 当前帧的卷号和剩余长度是否都有效。
     * false 时设备仍保留在 candidates 中，但不得参与 current 选择或剪切计算。
     * 该字段允许为 null，以兼容变更前已写入 Redis 的 status runtime。
     */
    private Boolean dataComplete;

    /**
     * 设备卷号连续缺失计数；有效卷号为 1，空候选也逐帧递增，超过
     * current_clear_threshold 后回到 1 并清除候选身份。旧版运行态为空时按 1 处理。
     */
    private Integer nullCount;

    private String coilNo;
    /**
     * 当前钢卷重复生产次数。
     */
    @JsonAlias({"productNo", "product_no"})
    private Integer repeatProdNo;
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
