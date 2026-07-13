package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.process.LengthMode;
import com.wisdri.tracking.domain.model.config.process.PointConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessTrackingAlgorithmImplTest {
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;
    private ProcessTrackingAlgorithmImpl algorithm;

    @BeforeEach
    void setUp() {
        runtimeRepositoryDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        algorithm = new ProcessTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", runtimeRepositoryDispatcher);
        ReflectionTestUtils.setField(algorithm, "abnormalDataHandlerDispatcher",
                mock(AbnormalDataHandlerDispatcher.class));
        ReflectionTestUtils.setField(algorithm, "pointEventHandlerDispatcher",
                mock(PointEventHandlerDispatcher.class));
        when(runtimeRepositoryDispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config()));
        doNothing().when(runtimeRepositoryDispatcher).saveRuntime(any(TrackingRuntime.class));
    }

    @Test
    void replacesSegmentRuntimeWithCurrentCalculationIncludingEmptyState() {
        List<ProcessResult> results = algorithm.calculate(input(values("C001", new BigDecimal("15.5"))));
        assertEquals(1, results.size());

        List<ProcessResult> emptyResults = algorithm.calculate(input(Collections.emptyMap()));
        assertTrue(emptyResults.isEmpty());

        ArgumentCaptor<TrackingRuntime> captor = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(runtimeRepositoryDispatcher, times(2)).saveRuntime(captor.capture());
        ProcessTrackingRuntime populated = (ProcessTrackingRuntime) captor.getAllValues().get(0);
        assertEquals("C001", populated.getSegments().get("S1").getCoilNo());
        assertEquals(new BigDecimal("15.5"), populated.getSegments().get("S1").getHeadLength());

        ProcessTrackingRuntime cleared = (ProcessTrackingRuntime) captor.getAllValues().get(1);
        assertTrue(cleared.getSegments().isEmpty());
    }

    private ProcessTrackingConfig config() {
        PointConfig coilPoint = PointConfig.builder().name("coil").build();
        PointConfig lengthPoint = PointConfig.builder().name("length").build();
        TrackingPointGroup group = TrackingPointGroup.builder()
                .coilNo(coilPoint)
                .length(Collections.singletonList(lengthPoint))
                .build();
        TrackingSection tracking = TrackingSection.builder()
                .lengthMode(LengthMode.COILER)
                .points(Collections.singletonList(group))
                .build();
        SegmentConfig segment = SegmentConfig.builder()
                .code("S1")
                .name("Segment 1")
                .lengthCorrect(BigDecimal.ZERO)
                .build();
        return ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .tracking(tracking)
                .segments(Collections.singletonList(segment))
                .build();
    }

    private TrackingInput input(Map<String, Object> values) {
        return TrackingInput.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .latestSnapshot(PointSnapshot.builder()
                        .values(values)
                        .receivedAt(Instant.now())
                        .build())
                .build();
    }

    private Map<String, Object> values(String coilNo, BigDecimal length) {
        Map<String, Object> values = new HashMap<>();
        values.put("coil", coilNo);
        values.put("length", length);
        return values;
    }
}
