package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

/**
 * 点位事件处理服务。
 *
 * <p>负责按跟踪类型检测并处理点位事件。</p>
 */
public interface PointEventHandler {
    /**
     * 是否支持指定跟踪类型。
     */
    boolean support(TrackingType trackingType);

    /**
     * 处理本次点位变化触发的业务事件。
     */
    void handle(TrackingInput input);
}
