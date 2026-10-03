package com.wisdri.tracking.domain.model.tracking.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCoilCacheEntry;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产跟踪任务时固化的状态上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusTrackingContext {
    /**
     * 状态算法所使用的点位快照接收时间。
     */
    private Instant receivedAt;

    /**
     * 状态计算时的启动条件点位值。
     */
    private BigDecimal startConditionPointValue;

    /** 生成该状态时最后有效的原始方向点值；与 directReverse 异或后得到实际方向。 */
    private Boolean rollingDirection;

    /** 生成该状态时采用的方向反转配置，用于校验 process 配置是否一致。 */
    private Boolean rollingDirectReverse;

    /** 生成该状态时的轧制道次号；为空表示尚未取得有效道次。 */
    private Integer passNo;

    /**
     * 各设备的连续采样窗口快照。
     */
    @Builder.Default
    private Map<String, StatusCandidateRuntime> candidates = new LinkedHashMap<>();

    /**
     * 当前开卷、卷取两端的完整识别状态。
     */
    @Builder.Default
    private Map<DeviceSide, StatusCurrentRuntime> current = new LinkedHashMap<>();

    /** 任务生产时固化的在产钢卷属性缓存；键为钢卷号，旧版消息可为空。 */
    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, StatusCoilCacheEntry> coilCache = new LinkedHashMap<>();

    /**
     * 当前状态帧检测到的钢卷号变化结果。
     */
    @Builder.Default
    private List<StatusResult> results = new ArrayList<>();

}
