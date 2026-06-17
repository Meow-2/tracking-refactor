package com.wisdri.tracking.domain.model.point;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 跟踪业务事件。
 *
 * <p>事件只表达发生了什么，具体是否热更新配置由应用层编排。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointEvent {
    /**
     * 机组代码。
     */
    private String unitCode;

    /**
     * 跟踪类型。
     */
    private TrackingType trackingType;

    /**
     * 事件类型。
     */
    private PointEventType eventType;

    /**
     * 事件发生时间。
     */
    private Instant occurredAt;
}
