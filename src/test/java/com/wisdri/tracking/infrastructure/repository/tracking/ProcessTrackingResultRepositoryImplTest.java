package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ProcessTrackingResultRepositoryImplTest {

    @Test
    void createsBooleanPointAsIndependentBooleanType() {
        ProcessTrackingResultRepositoryImpl repository = new ProcessTrackingResultRepositoryImpl();
        TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
        TimeSeriesStorageProperties storageProperties = new TimeSeriesStorageProperties();
        storageProperties.setDatabase("tracking_db");
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageProperties", storageProperties);
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());

        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segments(Collections.singletonList(SegmentConfig.builder()
                        .code("S1")
                        .points(Arrays.asList(
                                PointConfig.builder().name("count").type(PointDataType.SHORT).build(),
                                PointConfig.builder().name("ready").type(PointDataType.BOOLEAN).build()))
                        .build()))
                .build();

        repository.createTable(config);

        ArgumentCaptor<TimeSeriesTableRequest> captor = ArgumentCaptor.forClass(TimeSeriesTableRequest.class);
        verify(gateway).createTable(captor.capture());
        assertEquals("short", captor.getValue().getRule().stream()
                .filter(rule -> "count".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new).getDatatype());
        assertEquals("boolean", captor.getValue().getRule().stream()
                .filter(rule -> "ready".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new).getDatatype());
    }

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
