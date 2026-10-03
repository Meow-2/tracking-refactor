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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                                PointConfig.builder().name("ready").type(PointDataType.BOOLEAN).build(),
                                PointConfig.builder().name("cell_code").type(PointDataType.SHORT).build()))
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
        assertFalse(captor.getValue().getRule().stream()
                .filter(rule -> "count".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new).getIsTag());
        assertFalse(captor.getValue().getRule().stream()
                .filter(rule -> "ready".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new).getIsTag());
        TimeSeriesTableRule repeatProdNo = captor.getValue().getRule().stream()
                .filter(rule -> "repeat_prod_no".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("int", repeatProdNo.getDatatype());
        assertTrue(repeatProdNo.getIsTag());
        assertFalse(captor.getValue().getRule().stream()
                .filter(rule -> "head_length".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new).getIsTag());
        assertFalse(captor.getValue().getRule().stream()
                .filter(rule -> "speed".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new).getIsTag());
        assertEquals(1, captor.getValue().getRule().stream()
                .filter(rule -> "cell_code".equals(rule.getId())).count());
        TimeSeriesTableRule cellCode = captor.getValue().getRule().stream()
                .filter(rule -> "cell_code".equals(rule.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("string", cellCode.getDatatype());
        assertTrue(cellCode.getIsTag());
    }

    @Test
    void savesFixedColumnsAsTagsAndDynamicParametersAsFields() {
        ProcessTrackingResultRepositoryImpl repository = new ProcessTrackingResultRepositoryImpl();
        TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageProperties", new TimeSeriesStorageProperties());
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());
        Instant receivedAt = Instant.parse("2026-09-03T01:00:00Z");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("temperature", 850);
        parameters.put("cell_code", 999);

        repository.save(Collections.singletonList(ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segmentCode("S1")
                .cellCode("CP1001")
                .repeatProdNo(3)
                .headLength(new java.math.BigDecimal("12.5"))
                .speed(new java.math.BigDecimal("2.5"))
                .parameters(parameters)
                .receivedAt(receivedAt)
                .build()));

        ArgumentCaptor<TimeSeriesDataRequest> captor = ArgumentCaptor.forClass(TimeSeriesDataRequest.class);
        verify(gateway).saveColumn(anyString(), captor.capture());
        TimeSeriesDataValue repeatProdNo = captor.getValue().getValues().stream()
                .filter(value -> "repeat_prod_no".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, repeatProdNo.getV());
        assertEquals(receivedAt.toEpochMilli(), repeatProdNo.getT());
        assertTrue(repeatProdNo.getIsTag());
        assertFalse(captor.getValue().getValues().stream()
                .filter(value -> "head_length".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new).getIsTag());
        assertFalse(captor.getValue().getValues().stream()
                .filter(value -> "speed".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new).getIsTag());
        TimeSeriesDataValue temperature = captor.getValue().getValues().stream()
                .filter(value -> "temperature".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertFalse(temperature.getIsTag());
        assertEquals(1, captor.getValue().getValues().stream()
                .filter(value -> "cell_code".equals(value.getId())).count());
        TimeSeriesDataValue cellCode = captor.getValue().getValues().stream()
                .filter(value -> "cell_code".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("CP1001", cellCode.getV());
        assertTrue(cellCode.getIsTag());
    }

    @Test
    void savesNullCellCodeWhenConfiguredPointHasNoValidValue() {
        ProcessTrackingResultRepositoryImpl repository = new ProcessTrackingResultRepositoryImpl();
        TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());

        repository.save(Collections.singletonList(ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segmentCode("S1")
                .receivedAt(Instant.parse("2026-09-03T01:00:00Z"))
                .build()));

        ArgumentCaptor<TimeSeriesDataRequest> captor = ArgumentCaptor.forClass(TimeSeriesDataRequest.class);
        verify(gateway).saveColumn(anyString(), captor.capture());
        TimeSeriesDataValue cellCode = captor.getValue().getValues().stream()
                .filter(value -> "cell_code".equals(value.getId()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(null, cellCode.getV());
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
