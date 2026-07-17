package com.wisdri.tracking.domain.model.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 单个机组、单个跟踪算法的当前运行态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class TrackingRuntime {
    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪算法类型。
     */
    private TrackingType trackingType;

    /**
     * 模板实例编码；仅模板化跟踪类型使用，例如批次跟踪的 fb1。
     * <p>
     * 非模板跟踪类型保持为空，并且不会写入 Redis JSON，以兼容现有运行态结构。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String templateCode;

    /**
     * 运行态最后更新时间。
     */
    private Instant updatedAt;
}
