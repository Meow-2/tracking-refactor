package com.wisdri.tracking.domain.repository.runtime;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
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
    private TrackingRuntimeRepository shearRepository;
    private TrackingRuntimeRepositoryDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        processRepository = mock(TrackingRuntimeRepository.class);
        shearRepository = mock(TrackingRuntimeRepository.class);
        when(processRepository.support(TrackingType.PROCESS)).thenReturn(true);
        when(shearRepository.support(TrackingType.SHEAR)).thenReturn(true);
        dispatcher = new TrackingRuntimeRepositoryDispatcher();
        ReflectionTestUtils.setField(dispatcher, "repositories",
                Arrays.asList(processRepository, shearRepository));
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

        assertTrue(!dispatcher.findConfig("CP1", TrackingType.WELDING).isPresent());
        ProcessTrackingRuntime unsupported = ProcessTrackingRuntime.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.WELDING)
                .build();
        assertThrows(TrackingException.class, () -> dispatcher.saveRuntime(unsupported));
    }
}
