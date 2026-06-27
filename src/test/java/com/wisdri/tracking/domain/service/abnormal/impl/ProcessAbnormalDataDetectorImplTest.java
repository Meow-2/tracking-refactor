package com.wisdri.tracking.domain.service.abnormal.impl;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.abnormal.AbnormalType;
import com.wisdri.tracking.domain.model.config.process.PointConfig;
import com.wisdri.tracking.domain.model.config.process.PointDataType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessAbnormalDataDetectorImplTest {
    @Test
    void detectsOnlyEmptyPointsConfiguredUnderSegments() {
        ProcessAbnormalDataDetectorImpl detector = new ProcessAbnormalDataDetectorImpl();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("/seg1/temp", "");
        values.put("/seg1/speed", "120");
        values.put("/tracking/unused", "");
        TrackingInput input = TrackingInput.builder()
                .latestSnapshot(PointSnapshot.builder()
                        .values(values)
                        .build())
                .build();
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segments(Arrays.asList(SegmentConfig.builder()
                        .pointPrefix("/seg1/")
                        .points(Arrays.asList(point("temp"), point("speed")))
                        .build()))
                .build();

        List<AbnormalData> result = detector.detect(input, config);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPointCode()).isEqualTo("/seg1/temp");
        assertThat(result.get(0).getAbnormalType()).isEqualTo(AbnormalType.NULL_DATA);
    }

    private PointConfig point(String name) {
        return PointConfig.builder()
                .name(name)
                .type(PointDataType.FLOAT)
                .build();
    }
}
