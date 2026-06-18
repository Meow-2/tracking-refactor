package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 点位事件处理分发器。
 */
@Component
public class PointEventHandlerDispatcher {
    /**
     * 点位事件处理器列表。
     */
    @Resource
    private List<PointEventHandler<? extends TrackingConfig>> handlers;

    /**
     * 按跟踪类型选择处理器并处理点位事件。
     */
    @SuppressWarnings("unchecked")
    public void handle(TrackingInput input, TrackingConfig config) {
        if (input == null || config == null || handlers == null) {
            return;
        }
        for (PointEventHandler<? extends TrackingConfig> handler : handlers) {
            if (handler.support(input.getTrackingType())) {
                ((PointEventHandler<TrackingConfig>) handler).handle(input, config);
                return;
            }
        }
    }
}
