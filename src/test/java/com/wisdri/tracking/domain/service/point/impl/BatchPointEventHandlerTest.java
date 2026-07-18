package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.batch.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BatchPointEventHandlerTest {
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;
    private BatchPointEventHandler handler;

    @BeforeEach
    void setUp() {
        runtimeRepositoryDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        handler = new BatchPointEventHandler();
        ReflectionTestUtils.setField(handler, "runtimeRepositoryDispatcher", runtimeRepositoryDispatcher);
    }

    @Test
    void refreshesConfigWhenCoilSetChanges() {
        handler.handle(input(snapshot("N001", "S001"), snapshot("N002", "S001")), config());

        verify(runtimeRepositoryDispatcher).refreshConfig();
    }

    @Test
    void doesNotRefreshOnFirstFrameOrUnchangedCoilSet() {
        PointSnapshot latest = snapshot("N001", "S001");

        handler.handle(input(latest, null), config());
        handler.handle(input(latest, snapshot("S001", "\u200BN001\uFEFF")), config());

        verify(runtimeRepositoryDispatcher, never()).refreshConfig();
    }

    @Test
    void supportsOnlyBatchTracking() {
        assertTrue(handler.support(TrackingType.BATCH));
        assertFalse(handler.support(TrackingType.PROCESS));
    }

    private BatchTrackingConfig config() {
        return BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .tracking(TrackingSection.builder()
                        .pointPrefix("tracking/{template}_")
                        .points(Arrays.asList(
                                TrackingPointGroup.builder().segment("north")
                                        .coilNo(PointConfig.builder().name("north_coil_no").build()).build(),
                                TrackingPointGroup.builder().segment("south")
                                        .coilNo(PointConfig.builder().name("south_coil_no").build()).build()
                        ))
                        .build())
                .build();
    }

    private TrackingInput input(PointSnapshot latest, PointSnapshot previous) {
        return TrackingInput.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode("fb1")
                .latestSnapshot(latest)
                .previousSnapshot(previous)
                .build();
    }

    private PointSnapshot snapshot(String northCoilNo, String southCoilNo) {
        Map<String, Object> values = new HashMap<>();
        values.put("tracking/fb1_north_coil_no", northCoilNo);
        values.put("tracking/fb1_south_coil_no", southCoilNo);
        return PointSnapshot.builder().values(values).build();
    }
}
