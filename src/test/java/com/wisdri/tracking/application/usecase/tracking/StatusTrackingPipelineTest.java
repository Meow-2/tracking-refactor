package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import com.wisdri.tracking.domain.service.tracking.impl.StatusTrackingAlgorithmImpl;
import com.wisdri.tracking.domain.service.tracking.trace.TrackingStepLogger;
import com.wisdri.tracking.infrastructure.repository.tracking.StatusTrackingResultRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusTrackingPipelineTest {
    @Test
    void ignoresLegacyStatusTaskOnConsumer() {
        TrackingRuntimeRepositoryDispatcher runtimeDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        when(runtimeDispatcher.findConfigAs("CP1", TrackingType.STATUS, StatusTrackingConfig.class))
                .thenReturn(Optional.of(config()));
        when(runtimeDispatcher.findRuntimeAs(
                "CP1", TrackingType.STATUS,
                com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime.class))
                .thenReturn(Optional.empty());
        doNothing().when(runtimeDispatcher).saveRuntime(any());

        StatusTrackingAlgorithmImpl algorithm = new StatusTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(algorithm, "runtimeRepositoryDispatcher", runtimeDispatcher);
        ReflectionTestUtils.setField(algorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
        TrackingAlgorithmDispatcher algorithmDispatcher = new TrackingAlgorithmDispatcher();
        ReflectionTestUtils.setField(algorithmDispatcher, "algorithms", Collections.singletonList(algorithm));

        TrackingResultRepositoryDispatcher resultDispatcher = new TrackingResultRepositoryDispatcher();
        ReflectionTestUtils.setField(resultDispatcher, "repositories",
                Collections.singletonList(new StatusTrackingResultRepositoryImpl()));
        TrackingTaskConsumerUseCase useCase = new TrackingTaskConsumerUseCase();
        ReflectionTestUtils.setField(useCase, "trackingAlgorithmDispatcher", algorithmDispatcher);
        ReflectionTestUtils.setField(useCase, "trackingResultRepositoryDispatcher", resultDispatcher);

        assertThatCode(() -> useCase.consume(task())).doesNotThrowAnyException();
        verify(runtimeDispatcher, org.mockito.Mockito.never()).saveRuntime(any());
    }

    private StatusTrackingConfig config() {
        return StatusTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .tracking(StatusTrackingSection.builder()
                        .startCondition(StartCondition.builder()
                                .point(PointConfig.builder().name("run").build())
                                .threshold(BigDecimal.ONE)
                                .build())
                        .sampleCount(2)
                        .minLengthChange(BigDecimal.ZERO)
                        .points(Arrays.asList(
                                group("U1", DeviceSide.UNCOILER),
                                group("C1", DeviceSide.COILER)))
                        .build())
                .build();
    }

    private StatusPointGroup group(String code, DeviceSide side) {
        return StatusPointGroup.builder()
                .code(code)
                .name(code)
                .side(side)
                .coilNo(PointConfig.builder().name(code + "_coil").build())
                .remainingLength(PointConfig.builder().name(code + "_length").build())
                .build();
    }

    private TrackingTask task() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("run", BigDecimal.ONE);
        values.put("U1_coil", "U001");
        values.put("U1_length", new BigDecimal("100"));
        values.put("C1_coil", "C001");
        values.put("C1_length", new BigDecimal("10"));
        return TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .latestSnapshot(PointSnapshot.builder()
                        .values(values)
                        .receivedAt(Instant.parse("2026-08-12T07:00:00Z"))
                        .build())
                .build();
    }
}
