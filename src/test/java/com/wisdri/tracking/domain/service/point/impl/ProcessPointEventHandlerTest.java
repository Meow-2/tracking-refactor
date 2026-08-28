package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.process.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessPointEventHandlerTest {
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;
    private TrackingStepLogger trackingStepLogger;
    private ProcessPointEventHandler handler;

    @BeforeEach
    void setUp() {
        runtimeRepositoryDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        trackingStepLogger = mock(TrackingStepLogger.class);
        handler = new ProcessPointEventHandler();
        ReflectionTestUtils.setField(handler, "runtimeRepositoryDispatcher", runtimeRepositoryDispatcher);
        ReflectionTestUtils.setField(handler, "trackingStepLogger", trackingStepLogger);
    }

    @Test
    void logsCoilSetAndRefreshesOnlyWhenPreviousSetChanges() {
        ProcessTrackingConfig config = config();
        handler.handle(input(snapshot("C002"), snapshot("C001")), config);

        verify(runtimeRepositoryDispatcher).refreshConfig();
        verify(trackingStepLogger).log(any(TrackingInput.class), eq("钢卷集合检查"), anyMap());

        ProcessPointEventHandler firstFrameHandler = new ProcessPointEventHandler();
        TrackingRuntimeRepositoryDispatcher firstFrameRepository = mock(TrackingRuntimeRepositoryDispatcher.class);
        ReflectionTestUtils.setField(firstFrameHandler, "runtimeRepositoryDispatcher", firstFrameRepository);
        ReflectionTestUtils.setField(firstFrameHandler, "trackingStepLogger", mock(TrackingStepLogger.class));
        firstFrameHandler.handle(input(snapshot("C001"), null), config);
        verify(firstFrameRepository, never()).refreshConfig();
    }

    private ProcessTrackingConfig config() {
        return ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .tracking(TrackingSection.builder()
                        .pointPrefix("tracking/")
                        .points(Collections.singletonList(TrackingPointGroup.builder()
                                .coilNo(PointConfig.builder().name("coil_no").build())
                                .build()))
                        .build())
                .build();
    }

    private TrackingInput input(PointSnapshot latest, PointSnapshot previous) {
        return TrackingInput.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .latestSnapshot(latest)
                .previousSnapshot(previous)
                .build();
    }

    private PointSnapshot snapshot(String coilNo) {
        Map<String, Object> values = Collections.singletonMap("tracking/coil_no", coilNo);
        return PointSnapshot.builder().values(values).build();
    }
}
