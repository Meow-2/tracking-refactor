package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessTrackingResultRepositoryImplTest {

    @Test
    void skipsTrackingResultCreateAndSaveWhenDisabled() {
        ProcessTrackingResultRepositoryImpl repository = new ProcessTrackingResultRepositoryImpl();
        TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "trackingProperties", trackingResultDisabled());

        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segments(Collections.singletonList(SegmentConfig.builder().code("S1").build()))
                .build();
        ProcessResult result = ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segmentCode("S1")
                .build();

        repository.createTable(config);
        repository.save(Collections.singletonList(result));

        verify(gateway, never()).createTable(any());
        verify(gateway, never()).saveColumn(anyString(), any());
    }

    private TrackingProperties trackingResultDisabled() {
        TrackingProperties properties = new TrackingProperties();
        TrackingProperties.StoreSwitch trackingResult = new TrackingProperties.StoreSwitch();
        trackingResult.setEnabled(false);
        properties.getStorage().setTrackingResult(trackingResult);
        return properties;
    }
}
