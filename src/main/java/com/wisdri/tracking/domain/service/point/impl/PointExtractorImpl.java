package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.config.RollingConfig;
import com.wisdri.tracking.domain.model.config.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.service.point.PointExtractor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 默认点位提取服务。
 */
@Component
public class PointExtractorImpl implements PointExtractor {

    /**
     * 按配置提取 tracking 和 segments 中声明的点位。
     */
    @Override
    public PointSnapshot extract(Map<String, Object> rawValues, TrackingConfig config) {
        Map<String, PointValue> values = new LinkedHashMap<>();
        TrackingSection tracking = config.getTracking();
        if (tracking != null) {
            putTrackingPoint(values, rawValues, tracking.getPointPrefix(), tracking.getSpeedPoint(), false);
            StartCondition startCondition = tracking.getStartCondition();
            if (startCondition != null) {
                putTrackingPoint(values, rawValues, tracking.getPointPrefix(), startCondition.getPoint(), false);
            }
            RollingConfig rolling = tracking.getRolling();
            if (rolling != null) {
                putTrackingPoint(values, rawValues, tracking.getPointPrefix(), rolling.getDirectPoint(), false);
                putTrackingPoint(values, rawValues, tracking.getPointPrefix(), rolling.getPassNoPoint(), false);
            }
            if (tracking.getPoints() != null) {
                for (TrackingPointGroup group : tracking.getPoints()) {
                    putTrackingPoint(values, rawValues, tracking.getPointPrefix(), group.getCoilNoPoint(), true);
                    if (group.getLengthPoints() != null) {
                        for (String lengthPoint : group.getLengthPoints()) {
                            putTrackingPoint(values, rawValues, tracking.getPointPrefix(), lengthPoint, false);
                        }
                    }
                }
            }
        }
        if (config.getSegments() != null) {
            for (SegmentConfig segment : config.getSegments()) {
                if (segment.getPoints() != null) {
                    for (String point : segment.getPoints()) {
                        putTrackingPoint(values, rawValues, segment.getPointPrefix(), point, false);
                    }
                }
            }
        }
        return PointSnapshot.builder()
                .values(values)
                .receivedAt(Instant.now())
                .build();
    }

    /**
     * 从原始点位表中读取单个点位，并按短名写入快照。
     */
    private void putTrackingPoint(Map<String, PointValue> values,
                                  Map<String, Object> rawValues,
                                  String prefix,
                                  String point,
                                  boolean trimInvisible) {
        if (point == null) {
            return;
        }
        Object value = rawValues.get(prefix + point);
        if (value == null && rawValues.containsKey(point)) {
            value = rawValues.get(point);
        }
        if (trimInvisible && value != null) {
            value = String.valueOf(value).replaceAll("\\p{C}", "").trim();
        }
        values.put(point, PointValue.of(value));
    }
}
