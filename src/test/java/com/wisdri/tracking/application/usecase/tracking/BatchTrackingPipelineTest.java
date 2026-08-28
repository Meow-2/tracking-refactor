package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.config.batch.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.batch.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.batch.BatchResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import com.wisdri.tracking.domain.service.tracking.impl.BatchTrackingAlgorithmImpl;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchTrackingPipelineTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void carriesTemplateCodeFromTaskThroughAlgorithmToResultRepository() {
        TrackingRuntimeRepositoryDispatcher runtimeDispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        when(runtimeDispatcher.findConfigAs(
                "BAF1", TrackingType.BATCH, BatchTrackingConfig.class
        )).thenReturn(Optional.of(config()));
        doNothing().when(runtimeDispatcher).saveRuntime(any());

        BatchTrackingAlgorithmImpl batchAlgorithm = new BatchTrackingAlgorithmImpl();
        ReflectionTestUtils.setField(batchAlgorithm, "runtimeRepositoryDispatcher", runtimeDispatcher);
        ReflectionTestUtils.setField(batchAlgorithm, "pointEventHandlerDispatcher",
                mock(PointEventHandlerDispatcher.class));
        ReflectionTestUtils.setField(batchAlgorithm, "trackingStepLogger", mock(TrackingStepLogger.class));
        TrackingAlgorithmDispatcher algorithmDispatcher = new TrackingAlgorithmDispatcher();
        ReflectionTestUtils.setField(algorithmDispatcher, "algorithms", Collections.singletonList(batchAlgorithm));

        TrackingResultRepository batchRepository = mock(TrackingResultRepository.class);
        when(batchRepository.support(TrackingType.BATCH)).thenReturn(true);
        TrackingResultRepositoryDispatcher resultDispatcher = new TrackingResultRepositoryDispatcher();
        ReflectionTestUtils.setField(resultDispatcher, "repositories", Collections.singletonList(batchRepository));

        TrackingTaskConsumerUseCase useCase = new TrackingTaskConsumerUseCase();
        ReflectionTestUtils.setField(useCase, "trackingAlgorithmDispatcher", algorithmDispatcher);
        ReflectionTestUtils.setField(useCase, "trackingResultRepositoryDispatcher", resultDispatcher);

        useCase.consume(task());

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(batchRepository).save(captor.capture());
        List<TrackingResult> results = captor.getValue();
        assertEquals(1, results.size());
        BatchResult result = (BatchResult) results.get(0);
        assertEquals("fb1", result.getTemplateCode());
        assertEquals("north", result.getSegmentCode());
        assertEquals("N001", result.getCoilNo());
    }

    private BatchTrackingConfig config() {
        PointConfig productionStatus = PointConfig.builder().name("prod_status").build();
        PointConfig northCoilNo = PointConfig.builder().name("north_coil_no").build();
        TrackingSection tracking = TrackingSection.builder()
                .pointPrefix("tracking/{template}_")
                .startCondition(StartCondition.builder()
                        .point(productionStatus)
                        .threshold(BigDecimal.ZERO)
                        .build())
                .points(Collections.singletonList(TrackingPointGroup.builder()
                        .segment("north")
                        .coilNo(northCoilNo)
                        .build()))
                .build();
        SegmentConfig north = SegmentConfig.builder()
                .code("north")
                .name("北侧")
                .build();
        return BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .tracking(tracking)
                .segments(Collections.singletonList(north))
                .build();
    }

    private TrackingInput task() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tracking/fb1_prod_status", 1);
        values.put("tracking/fb1_north_coil_no", "N001");
        return TrackingInput.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode("fb1")
                .latestSnapshot(PointSnapshot.builder()
                        .values(values)
                        .receivedAt(Instant.parse("2026-07-17T08:00:00Z"))
                        .build())
                .build();
    }
}
