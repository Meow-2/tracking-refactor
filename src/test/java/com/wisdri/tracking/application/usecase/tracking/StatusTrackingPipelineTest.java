package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StatusTrackingPipelineTest {
    @Test
    void ignoresLegacyStatusTaskOnConsumer() {
        TrackingAlgorithmDispatcher algorithmDispatcher = mock(TrackingAlgorithmDispatcher.class);
        TrackingResultRepositoryDispatcher resultDispatcher = mock(TrackingResultRepositoryDispatcher.class);
        TrackingTaskConsumerUseCase useCase = new TrackingTaskConsumerUseCase();
        ReflectionTestUtils.setField(useCase, "trackingAlgorithmDispatcher", algorithmDispatcher);
        ReflectionTestUtils.setField(useCase, "trackingResultRepositoryDispatcher", resultDispatcher);

        assertThatCode(() -> useCase.consume(task())).doesNotThrowAnyException();
        verifyNoInteractions(algorithmDispatcher, resultDispatcher);
    }

    private TrackingTask task() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("run", 1);
        values.put("U1_coil", "U001");
        values.put("U1_length", 100);
        values.put("C1_coil", "C001");
        values.put("C1_length", 10);
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
