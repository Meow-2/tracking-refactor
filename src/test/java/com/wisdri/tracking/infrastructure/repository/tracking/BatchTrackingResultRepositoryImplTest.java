package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.batch.BatchResult;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataValue;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRule;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BatchTrackingResultRepositoryImplTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-07-17T08:00:00Z");
    private static final long NORTH_TIMESTAMP = RECEIVED_AT.toEpochMilli() + 1;

    private TimeSeriesStorageGateway gateway;
    private BatchTrackingResultRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        gateway = mock(TimeSeriesStorageGateway.class);
        repository = new BatchTrackingResultRepositoryImpl();
        TimeSeriesStorageProperties storageProperties = new TimeSeriesStorageProperties();
        storageProperties.setDatabase("tracking_db");
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageProperties", storageProperties);
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());
    }

    @Test
    void createsOnlySharedLowercaseBatchTableWithUnionColumns() {
        repository.createTable(config());

        ArgumentCaptor<TimeSeriesTableRequest> captor = ArgumentCaptor.forClass(TimeSeriesTableRequest.class);
        verify(gateway).createTable(captor.capture());
        TimeSeriesTableRequest request = captor.getValue();
        assertEquals("baf1_batch", request.getMeasurement());
        assertEquals("tracking_db", request.getBucket());
        assertRule(request.getRule(), "fb_code", "int", true);
        assertRule(request.getRule(), "segment_code", "int", false);
        assertRule(request.getRule(), "coil_no", "string", true);
        assertRule(request.getRule(), "prod_status", "float", false);
        assertRule(request.getRule(), "head_length", "float", false);
        assertRule(request.getRule(), "speed", "float", false);
        assertRule(request.getRule(), "pass_no", "int", true);
        assertRule(request.getRule(), "shared", "string", false);
        assertRule(request.getRule(), "north_temp", "float", false);
        assertRule(request.getRule(), "south_count", "short", false);
        assertRule(request.getRule(), "south_ready", "boolean", false);
        assertEquals(1, request.getRule().stream()
                .filter(rule -> "shared".equals(rule.getId()))
                .count());
    }

    @Test
    void savesTemplateCodeAsFbCodeAndUsesReceivedAt() {
        BatchResult north = result("fb1", "north", "N001");
        BatchResult south = result("fb1", "south", "S001");

        repository.save(Arrays.asList(north, south));

        ArgumentCaptor<TimeSeriesDataRequest> captor = ArgumentCaptor.forClass(TimeSeriesDataRequest.class);
        verify(gateway, times(2)).saveColumn(eq("baf1_batch"), captor.capture());
        TimeSeriesDataRequest northRequest = captor.getAllValues().get(0);
        assertEquals(NORTH_TIMESTAMP, northRequest.getTimestamp());
        assertValue(northRequest.getValues(), "fb_code", 1, true, NORTH_TIMESTAMP);
        assertValue(northRequest.getValues(), "segment_code", 1, false, NORTH_TIMESTAMP);
        assertValue(northRequest.getValues(), "coil_no", "N001", true, NORTH_TIMESTAMP);
        assertValue(northRequest.getValues(), "prod_status", new BigDecimal("1"), false, NORTH_TIMESTAMP);
        assertValue(northRequest.getValues(), "shared", "north-value", false, NORTH_TIMESTAMP);
        assertFalse(northRequest.getValues().stream().anyMatch(value -> "empty".equals(value.getId())));
        assertFalse(northRequest.getValues().stream().anyMatch(value -> "head_length".equals(value.getId())));
        assertFalse(northRequest.getValues().stream().anyMatch(value -> "speed".equals(value.getId())));
        assertFalse(northRequest.getValues().stream().anyMatch(value -> "pass_no".equals(value.getId())));

        TimeSeriesDataRequest southRequest = captor.getAllValues().get(1);
        assertEquals(RECEIVED_AT.toEpochMilli(), southRequest.getTimestamp());
        assertValue(southRequest.getValues(), "segment_code", 0, false);
    }

    @Test
    void rejectsConflictingDynamicColumnTypes() {
        BatchTrackingConfig config = config();
        config.getSegments().get(0).setPoints(Collections.singletonList(
                point("shared", PointDataType.SHORT)));

        assertThrows(TrackingException.class, () -> repository.createTable(config));
    }

    @Test
    void skipsBatchResultCreateAndSaveWhenDisabled() {
        TrackingProperties properties = new TrackingProperties();
        properties.getStorage().getBatch().setEnabled(false);
        ReflectionTestUtils.setField(repository, "trackingProperties", properties);

        repository.createTable(config());
        repository.save(Collections.singletonList(result("fb1", "north", "N001")));

        verify(gateway, never()).createTable(any());
        verify(gateway, never()).saveColumn(anyString(), any());
    }

    private BatchTrackingConfig config() {
        SegmentConfig common = segment("common",
                point("shared", PointDataType.STRING));
        SegmentConfig north = segment("north",
                point("shared", PointDataType.STRING),
                point("north_temp", PointDataType.FLOAT));
        SegmentConfig south = segment("south",
                point("south_count", PointDataType.SHORT),
                point("south_ready", PointDataType.BOOLEAN));
        return BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .segments(Arrays.asList(common, north, south))
                .build();
    }

    private SegmentConfig segment(String code, PointConfig... points) {
        return SegmentConfig.builder()
                .code(code)
                .points(Arrays.asList(points))
                .build();
    }

    private PointConfig point(String name, PointDataType type) {
        return PointConfig.builder().name(name).type(type).build();
    }

    private BatchResult result(String templateCode, String segmentCode, String coilNo) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("shared", segmentCode + "-value");
        parameters.put("empty", null);
        return BatchResult.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode(templateCode)
                .segmentCode(segmentCode)
                .coilNo(coilNo)
                .productionStatus(BigDecimal.ONE)
                .parameters(parameters)
                .receivedAt(RECEIVED_AT)
                .build();
    }

    private void assertRule(List<TimeSeriesTableRule> rules,
                            String id,
                            String datatype,
                            boolean tag) {
        TimeSeriesTableRule rule = rules.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals(datatype, rule.getDatatype());
        assertEquals(tag, rule.getIsTag());
    }

    private void assertValue(List<TimeSeriesDataValue> values,
                             String id,
                             Object expected,
                             boolean tag) {
        assertValue(values, id, expected, tag, RECEIVED_AT.toEpochMilli());
    }

    private void assertValue(List<TimeSeriesDataValue> values,
                             String id,
                             Object expected,
                             boolean tag,
                             long expectedTimestamp) {
        TimeSeriesDataValue value = values.stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals(expected, value.getV());
        assertEquals(tag, value.getIsTag());
        assertTrue(value.getQ());
        assertEquals(expectedTimestamp, value.getT());
    }
}
