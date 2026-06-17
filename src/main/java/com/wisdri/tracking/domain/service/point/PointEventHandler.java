package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.point.PointEvent;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;

import java.util.List;

/**
 * 点位事件处理服务。
 *
 * <p>负责处理点位事件检测结果，检测器只判断事件是否发生。</p>
 */
public interface PointEventHandler {
    /**
     * 处理本次跟踪输入检测出的点位事件。
     */
    void handle(TrackingInput input, List<PointEvent> events);
}
