package com.wisdri.tracking.domain.model.runtime.status;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    /** 本轮计算采用的方向反转配置；热更新后若值变化，必须清空旧方向的采样窗口。 */
    private Boolean rollingDirectReverse;
    /** 最近一次有效的轧制道次号。 */
    private Integer passNo;
    /** 当前道次供质量接口结算的数据；未形成完整有效采样时为空。 */
    private RollingPassOutputState passOutput;
    private Map<String, StatusCandidateRuntime> candidates = new LinkedHashMap<>();
    private Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();

    /**
     * 本机组仍在设备或产线卷号点位上的钢卷属性；键为钢卷号，旧版运行态可为空。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, StatusCoilCacheEntry> coilCache = new LinkedHashMap<>();

    /**
     * status 仅以点位帧接收时间标识状态时刻，不记录处理更新时间。
     */
    @Override
    @JsonIgnore
    public Instant getUpdatedAt() {
        return super.getUpdatedAt();
    }
}
