package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossSegmentConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.ironloss.IronLossResult;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IronLossTrackingResultRepositoryImplTest {
    private IronLossTrackingResultRepositoryImpl repository;
    private TimeSeriesStorageGateway gateway;
    private TrackingProperties trackingProperties;

    @BeforeEach
    void setUp() {
        repository = new IronLossTrackingResultRepositoryImpl();
        gateway = mock(TimeSeriesStorageGateway.class);
        trackingProperties = new TrackingProperties();
        TimeSeriesStorageProperties storageProperties = new TimeSeriesStorageProperties();
        storageProperties.setDatabase("digital_coil");
        ReflectionTestUtils.setField(repository, "timeSeriesStorageGateway", gateway);
        ReflectionTestUtils.setField(repository, "timeSeriesStorageProperties", storageProperties);
        ReflectionTestUtils.setField(repository, "trackingProperties", trackingProperties);
    }

    @Test
    void createsProcessStyleIronlossTableWithFixedAndDynamicColumns() {
        repository.createTable(config());

        ArgumentCaptor<TimeSeriesTableRequest> captor = ArgumentCaptor.forClass(TimeSeriesTableRequest.class);
        verify(gateway).createTable(captor.capture());
        TimeSeriesTableRequest request = captor.getValue();
        assertThat(request.getMeasurement()).isEqualTo("FCL1_ironloss_iron_loss");
        assertThat(request.getBucket()).isEqualTo("digital_coil");
        assertThat(request.getRule()).extracting("id", "datatype", "isTag")
                .containsExactly(tuple("coil_no", "string", true),
                        tuple("cell_code", "string", true),
                        tuple("repeat_prod_no", "int", true),
                        tuple("head_length", "float", false),
                        tuple("iron_loss", "float", false));
    }

    @Test
    void savesUnmatchedRepeatProdNoAsNullAndKeepsOriginalParameterValue() {
        Instant receivedAt = Instant.parse("2026-10-03T00:00:00Z");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("iron_loss", new BigDecimal("1.234567890123456789"));
        repository.save(Collections.singletonList(IronLossResult.builder()
                .unitCode("FCL1").trackingType(TrackingType.IRONLOSS).segmentCode("iron_loss")
                .receivedAt(receivedAt).coilNo("C001").headLength(new BigDecimal("0.1"))
                .cellCode("FCL1001").parameters(parameters).build()));

        ArgumentCaptor<TimeSeriesDataRequest> captor = ArgumentCaptor.forClass(TimeSeriesDataRequest.class);
        verify(gateway).saveColumn(eq("FCL1_ironloss_iron_loss"), captor.capture());
        TimeSeriesDataRequest request = captor.getValue();
        assertThat(request.getTimestamp()).isEqualTo(receivedAt.toEpochMilli());
        assertThat(request.getValues()).extracting("id", "v")
                .containsExactly(tuple("coil_no", "C001"), tuple("cell_code", "FCL1001"),
                        tuple("repeat_prod_no", null), tuple("head_length", new BigDecimal("0.1")),
                        tuple("iron_loss", new BigDecimal("1.234567890123456789")));
    }

    @Test
    void disabledStorageDoesNotCreateOrWriteTable() {
        trackingProperties.getStorage().getIronloss().setEnabled(false);

        repository.createTable(config());
        repository.save(Collections.singletonList(IronLossResult.builder()
                .unitCode("FCL1").segmentCode("iron_loss").build()));

        verify(gateway, never()).createTable(any());
        verify(gateway, never()).saveColumn(any(), any());
    }

    private IronLossTrackingConfig config() {
        IronLossSegmentConfig segment = new IronLossSegmentConfig();
        segment.setCode("iron_loss");
        segment.setPoints(Arrays.asList(
                PointConfig.builder().name("cell_code").type(PointDataType.SHORT).build(),
                PointConfig.builder().name("iron_loss").type(PointDataType.FLOAT).build()));
        return IronLossTrackingConfig.builder().unitCode("FCL1")
                .segments(Collections.singletonList(segment)).build();
    }
}
