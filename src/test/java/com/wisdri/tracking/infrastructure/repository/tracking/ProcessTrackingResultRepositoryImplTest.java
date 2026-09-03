package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataValue;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRule;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        TimeSeriesTableRule productNo = captor.getValue().getRule().stream()
                .filter(rule -> "in_mat_prod_no".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("int", productNo.getDatatype());
        assertFalse(productNo.getIsTag());
    }

    @Test
    void savesProductNumberAsNonTagValue() {
        ProcessTrackingResultRepositoryImpl repository = new ProcessTrackingResultRepositoryImpl();
        TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageProperties", new TimeSeriesStorageProperties());
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());
        Instant receivedAt = Instant.parse("2026-09-03T01:00:00Z");

        repository.save(Collections.singletonList(ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segmentCode("S1")
                .inMatNoProdNo(3)
                .receivedAt(receivedAt)
                .build()));

        ArgumentCaptor<TimeSeriesDataRequest> captor = ArgumentCaptor.forClass(TimeSeriesDataRequest.class);
        verify(gateway).saveColumn(anyString(), captor.capture());
        TimeSeriesDataValue productNo = captor.getValue().getValues().stream()
                .filter(value -> "in_mat_prod_no".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, productNo.getV());
        assertEquals(receivedAt.toEpochMilli(), productNo.getT());
        assertFalse(productNo.getIsTag());
    }

    @Test
    void skipsTrackingResultCreateAndSaveWhenDisabled() {
        ProcessTrackingResultRepositoryImpl repository = new ProcessTrackingResultRepositoryImpl();
        TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "trackingProperties", processStorageDisabled());

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

    private TrackingProperties processStorageDisabled() {
        TrackingProperties properties = new TrackingProperties();
        properties.getStorage().getProcess().setEnabled(false);
        return properties;
    }
}
