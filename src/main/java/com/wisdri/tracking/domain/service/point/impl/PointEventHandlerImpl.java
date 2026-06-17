package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.point.PointEvent;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.domain.service.point.PointEventHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 默认点位事件处理实现。
 */
@Component
public class PointEventHandlerImpl implements PointEventHandler {
    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingConfigRepository configRepository;

    /**
     * 点位事件发生后刷新当前跟踪配置。
     */
    @Override
    public void handle(TrackingInput input, List<PointEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        for (PointEvent event : events) {
            configRepository.refresh(event.getUnitCode(), event.getTrackingType());
        }
    }
}
