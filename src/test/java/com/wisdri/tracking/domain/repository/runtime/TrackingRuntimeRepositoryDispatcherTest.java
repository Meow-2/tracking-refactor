package com.wisdri.tracking.domain.repository.runtime;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.process.ProcessTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingRuntimeRepositoryDispatcherTest {
    private TrackingRuntimeRepository processRepository;
    private TrackingRuntimeRepository batchRepository;
    private TrackingRuntimeRepository shearRepository;
    private TrackingRuntimeRepositoryDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        processRepository = mock(TrackingRuntimeRepository.class);
        batchRepository = mock(TrackingRuntimeRepository.class);
        shearRepository = mock(TrackingRuntimeRepository.class);
        when(processRepository.support(TrackingType.PROCESS)).thenReturn(true);
        when(batchRepository.support(TrackingType.BATCH)).thenReturn(true);
        when(shearRepository.support(TrackingType.SHEAR)).thenReturn(true);
        dispatcher = new TrackingRuntimeRepositoryDispatcher();
        ReflectionTestUtils.setField(dispatcher, "repositories",
                Arrays.asList(processRepository, batchRepository, shearRepository));
    }

    @Test
    void routesTemplateRuntimeByTrackingTypeAndTemplateCode() {
        BatchTrackingRuntime runtime = BatchTrackingRuntime.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode("fb1")
                .build();
        when(batchRepository.findRuntimeAs(
                "BAF1", TrackingType.BATCH, "fb1", BatchTrackingRuntime.class
        )).thenReturn(Optional.of(runtime));

        assertEquals(runtime, dispatcher.findRuntimeAs(
                "BAF1", TrackingType.BATCH, "fb1", BatchTrackingRuntime.class
        ).orElseThrow(AssertionError::new));
        verify(batchRepository).findRuntimeAs(
                "BAF1", TrackingType.BATCH, "fb1", BatchTrackingRuntime.class);
    }

    @Test
    void routesConfigAndRuntimeByTrackingType() {
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();
        ProcessTrackingRuntime runtime = ProcessTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();
        when(processRepository.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        )).thenReturn(Optional.of(config));
        when(processRepository.findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class
        )).thenReturn(Optional.of(runtime));

        assertEquals(config, dispatcher.findConfigAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingConfig.class
        ).orElseThrow(AssertionError::new));
        assertEquals(runtime, dispatcher.findRuntimeAs(
                "CP1", TrackingType.PROCESS, ProcessTrackingRuntime.class
        ).orElseThrow(AssertionError::new));

        dispatcher.saveRuntime(runtime);
        verify(processRepository).saveRuntime(runtime);
    }

    @Test
    void refreshesAllRepositoriesAndHandlesUnsupportedTypes() {
        dispatcher.refreshConfig();
        verify(processRepository).refreshConfig();
        verify(shearRepository).refreshConfig();

        assertTrue(!dispatcher.findConfig("CP1", TrackingType.TRIMMING).isPresent());
        ProcessTrackingRuntime unsupported = ProcessTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.TRIMMING)
                .build();
        assertThrows(TrackingException.class, () -> dispatcher.saveRuntime(unsupported));
    }
}
