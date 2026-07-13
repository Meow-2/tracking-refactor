package com.wisdri.tracking.domain.model.runtime;

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
     * 运行态最后更新时间。
     */
    private Instant updatedAt;
}
