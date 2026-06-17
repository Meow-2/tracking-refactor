package com.wisdri.tracking.domain.service.abnormal.impl;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.abnormal.AbnormalType;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataDetector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认异常数据检测服务。
 */
@Component
public class AbnormalDataDetectorImpl implements AbnormalDataDetector {

    /**
     * 检测最新快照中的空点位数据。
     */
    @Override
    public List<AbnormalData> detect(PointSnapshot latest, PointSnapshot previous, TrackingConfig config) {
        List<AbnormalData> result = new ArrayList<>();
        if (latest == null || latest.getValues() == null) {
            return result;
        }
        for (Map.Entry<String, PointValue> entry : latest.getValues().entrySet()) {
            Object rawValue = entry.getValue() == null ? null : entry.getValue().getRawValue();
            if (rawValue == null || String.valueOf(rawValue).trim().isEmpty()) {
                result.add(AbnormalData.builder()
                        .unitCode(config.getUnitCode())
                        .trackingType(config.getTrackingType())
                        .pointCode(entry.getKey())
                        .abnormalType(AbnormalType.NULL_DATA)
                        .rawValue(rawValue)
                        .reason("点位值为空")
                        .occurredAt(Instant.now())
                        .build());
            }
        }
        return result;
    }
}
