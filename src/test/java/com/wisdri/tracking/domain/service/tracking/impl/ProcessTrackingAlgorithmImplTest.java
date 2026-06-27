package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.process.LengthMode;
import com.wisdri.tracking.domain.model.config.process.PointConfig;
import com.wisdri.tracking.domain.model.config.process.PointDataType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessTrackingAlgorithmImplTest {

    @Test
    void calculateWritesSegmentCodeAndDisplayNameSeparately() {
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .tracking(TrackingSection.builder()
                        .pointPrefix("/tracking/")
                        .speedPoint(point("speed"))
                        .lengthMode(LengthMode.COILER)
                        .points(Arrays.asList(TrackingPointGroup.builder()
                                .coilNo(point("coil_no"))
                                .length(Arrays.asList(point("length")))
                                .build()))
                        .build())
                .segments(Arrays.asList(SegmentConfig.builder()
                        .code("nof")
                        .name("NOF段")
                        .pointPrefix("/tech/nof/")
                        .lengthCorrect(BigDecimal.ZERO)
                        .build()))
                .build();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("/tracking/speed", BigDecimal.ONE);
        values.put("/tracking/coil_no", "C001");
        values.put("/tracking/length", BigDecimal.TEN);
        Instant receivedAt = Instant.parse("2026-06-26T09:30:00Z");
        TrackingInput input = TrackingInput.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .latestSnapshot(PointSnapshot.builder().values(values).receivedAt(receivedAt).build())
                .build();
        TrackingConfigRepository configRepository = mock(TrackingConfigRepository.class);
        when(configRepository.findAs("CP1", TrackingType.PROCESS, ProcessTrackingConfig.class)).thenReturn(Optional.of(config));
        ProcessTrackingAlgorithmImpl algorithm = new ProcessTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "configRepository", configRepository);
        ReflectionTestUtils.setField(algorithm, "abnormalDataHandlerDispatcher", mock(AbnormalDataHandlerDispatcher.class));
        ReflectionTestUtils.setField(algorithm, "pointEventHandlerDispatcher", mock(PointEventHandlerDispatcher.class));

        List<ProcessResult> results = algorithm.calculate(input);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSegmentCode()).isEqualTo("nof");
        assertThat(results.get(0).getSegmentName()).isEqualTo("NOF段");
        assertThat(results.get(0).getReceivedAt()).isEqualTo(receivedAt);
    }

    private PointConfig point(String name) {
        return PointConfig.builder()
                .name(name)
                .type(PointDataType.FLOAT)
                .build();
    }
}
