package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.point.PointEvent;
import com.wisdri.tracking.domain.model.point.PointEventType;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.service.point.PointEventDetector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认跟踪事件检测服务。
 */
@Component
public class PointEventDetectorImpl implements PointEventDetector {

    /**
     * 检测最新快照和上一条快照之间的业务事件。
     */
    @Override
    public List<PointEvent> detect(PointSnapshot latest, PointSnapshot previous, TrackingConfig config) {
        if (previous == null) {
            return Collections.emptyList();
        }
        Set<String> latestCoils = coilSet(latest, config);
        Set<String> previousCoils = coilSet(previous, config);
        if (!latestCoils.equals(previousCoils)) {
            return Collections.singletonList(PointEvent.builder()
                    .unitCode(config.getUnitCode())
                    .trackingType(config.getTrackingType())
                    .eventType(PointEventType.COIL_SET_CHANGED)
                    .occurredAt(Instant.now())
                    .build());
        }
        return Collections.emptyList();
    }

    /**
     * 提取配置中所有钢卷号点位对应的非空钢卷号集合。
     */
    private Set<String> coilSet(PointSnapshot snapshot, TrackingConfig config) {
        Set<String> coils = new LinkedHashSet<>();
        if (snapshot == null || !(config instanceof ProcessTrackingConfig)) {
            return coils;
        }
        ProcessTrackingConfig processConfig = (ProcessTrackingConfig) config;
        if (processConfig.getTracking() == null || processConfig.getTracking().getPoints() == null) {
            return coils;
        }
        for (TrackingPointGroup group : processConfig.getTracking().getPoints()) {
            snapshot.value(group.getCoilNoPoint())
                    .map(PointValue::stringValue)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .ifPresent(coils::add);
        }
        return coils;
    }
}
