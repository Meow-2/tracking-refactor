package com.wisdri.tracking.domain.model.runtime.status;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 状态跟踪的采样窗口和当前识别结果。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StatusTrackingRuntime extends TrackingRuntime {
    /**
     * 本轮状态计算使用的点位快照接收时间。
     */
    private Instant receivedAt;

    private BigDecimal startConditionPointValue;
    /** 最近一次有效的原始轧制方向点值。 */
    private Boolean rollingDirection;
    /** 最近一次有效的轧制道次号。 */
    private Integer passNo;
    private Map<String, StatusCandidateRuntime> candidates = new LinkedHashMap<>();
    private Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();

    /**
     * status 仅以点位帧接收时间标识状态时刻，不记录处理更新时间。
     */
    @Override
    @JsonIgnore
    public Instant getUpdatedAt() {
        return super.getUpdatedAt();
    }
}
