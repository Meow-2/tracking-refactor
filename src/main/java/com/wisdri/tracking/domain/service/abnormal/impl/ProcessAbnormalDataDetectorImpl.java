package com.wisdri.tracking.domain.service.abnormal.impl;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.abnormal.AbnormalType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataDetector;
import com.wisdri.tracking.domain.service.point.PointReader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 过程跟踪异常数据处理实现。
 */
@Component
public class ProcessAbnormalDataDetectorImpl implements AbnormalDataDetector<ProcessTrackingConfig> {

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.PROCESS == trackingType;
    }
    /**
     * 检测最新快照中的空点位数据。
     */
    @Override
    public List<AbnormalData> detect(TrackingInput input, ProcessTrackingConfig config) {
        List<AbnormalData> result = new ArrayList<>();
        if (config == null) {
            return result;
        }
        PointSnapshot latest = input.getLatestSnapshot();
        if (latest == null || latest.getValues() == null) {
            return result;
        }
        if (config.getSegments() == null) {
            return result;
        }
        for (SegmentConfig segment : config.getSegments()) {
            if (segment == null || segment.getPoints() == null) {
                continue;
            }
            for (String point : segment.getPoints()) {
                String pointPath = PointReader.pathResolve(segment.getPointPrefix(), point);
                Object rawValue = PointReader.rawValue(latest, pointPath);
                if (rawValue == null || String.valueOf(rawValue).trim().isEmpty()) {
                    result.add(AbnormalData.builder()
                            .unitCode(config.getUnitCode())
                            .trackingType(config.getTrackingType())
                            .pointCode(pointPath)
                            .abnormalType(AbnormalType.NULL_DATA)
                            .rawValue(rawValue)
                            .reason("点位值为空")
                            .occurredAt(Instant.now())
                            .build());
                }
            }
        }
        return result;
    }
}
