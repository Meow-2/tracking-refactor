package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.PointConfig;
import com.wisdri.tracking.domain.model.config.process.PointDataType;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRule;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.service.feign.client.TimeSeriesStorageFeignClient;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProcessTrackingResultRepositoryImplTest {

    private final TimeSeriesStorageGateway gateway = mock(TimeSeriesStorageGateway.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(TimeSeriesStorageFeignClient.class, () -> mock(TimeSeriesStorageFeignClient.class))
            .withBean(TimeSeriesStorageGateway.class, () -> gateway)
            .withBean(ProcessTrackingResultRepositoryImpl.class)
            .withPropertyValues("time-series-storage.database=configured_tracking");

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(gateway);
    }

    @Test
    void createTableUsesConfiguredTimeSeriesStorageDatabaseAsBucket() {
        contextRunner.run(context -> {
            ProcessTrackingResultRepositoryImpl repository = context.getBean(ProcessTrackingResultRepositoryImpl.class);
            ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                    .unitCode("CP1")
                    .trackingType(TrackingType.PROCESS)
                    .segments(Arrays.asList(SegmentConfig.builder().code("nof").name("NOF段").build()))
                    .build();

            repository.createTable(config);

            ArgumentCaptor<TimeSeriesTableRequest> requestCaptor = ArgumentCaptor.forClass(TimeSeriesTableRequest.class);
            verify(gateway).createTable(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getBucket()).isEqualTo("configured_tracking");
        });
    }

    @Test
    void createTableCreatesOneTableForEachSegment() {
        contextRunner.run(context -> {
            ProcessTrackingResultRepositoryImpl repository = context.getBean(ProcessTrackingResultRepositoryImpl.class);
            ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                    .unitCode("CP1")
                    .trackingType(TrackingType.PROCESS)
                    .segments(Arrays.asList(
                            SegmentConfig.builder().code("nof").name("NOF段").build(),
                            SegmentConfig.builder().code("rtf").name("RTF段").build()
                    ))
                    .build();

            repository.createTable(config);

            ArgumentCaptor<TimeSeriesTableRequest> requestCaptor = ArgumentCaptor.forClass(TimeSeriesTableRequest.class);
            verify(gateway, times(2)).createTable(requestCaptor.capture());
            assertThat(requestCaptor.getAllValues())
                    .extracting(TimeSeriesTableRequest::getMeasurement)
                    .containsExactly("CP1_process_nof", "CP1_process_rtf");
        });
    }

    @Test
    void createTableUsesFixedNonTagRulesAndSegmentPointTagRules() {
        contextRunner.run(context -> {
            ProcessTrackingResultRepositoryImpl repository = context.getBean(ProcessTrackingResultRepositoryImpl.class);
            ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                    .unitCode("CP1")
                    .trackingType(TrackingType.PROCESS)
                    .segments(Arrays.asList(SegmentConfig.builder()
                            .code("nof")
                            .name("NOF段")
                            .points(Arrays.asList(
                                    point("entry_temp", PointDataType.FLOAT),
                                    point("exit_temp", PointDataType.STRING),
                                    point("ready", PointDataType.BOOL)
                            ))
                            .build()))
                    .build();

            repository.createTable(config);

            ArgumentCaptor<TimeSeriesTableRequest> requestCaptor = ArgumentCaptor.forClass(TimeSeriesTableRequest.class);
            verify(gateway).createTable(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getRule())
                    .extracting(TimeSeriesTableRule::getId, TimeSeriesTableRule::getDatatype, TimeSeriesTableRule::getIsTag)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("coil_no", "string", false),
                            org.assertj.core.groups.Tuple.tuple("head_length", "float", false),
                            org.assertj.core.groups.Tuple.tuple("speed", "float", false),
                            org.assertj.core.groups.Tuple.tuple("pass_no", "int", false),
                            org.assertj.core.groups.Tuple.tuple("entry_temp", "float", true),
                            org.assertj.core.groups.Tuple.tuple("exit_temp", "string", true),
                            org.assertj.core.groups.Tuple.tuple("ready", "int", true)
                    );
        });
    }

    @Test
    void saveWritesEachResultToItsSegmentTable() {
        contextRunner.run(context -> {
            ProcessTrackingResultRepositoryImpl repository = context.getBean(ProcessTrackingResultRepositoryImpl.class);
            List<ProcessResult> results = Arrays.asList(
                    ProcessResult.builder()
                            .unitCode("CP1")
                            .trackingType(TrackingType.PROCESS)
                            .segmentCode("nof")
                            .segmentName("NOF段")
                            .generatedAt(Instant.parse("2026-06-26T00:00:00Z"))
                            .build(),
                    ProcessResult.builder()
                            .unitCode("CP1")
                            .trackingType(TrackingType.PROCESS)
                            .segmentCode("rtf")
                            .segmentName("RTF段")
                            .generatedAt(Instant.parse("2026-06-26T00:00:01Z"))
                            .build()
            );

            repository.save(results);

            verify(gateway).saveColumn(eq("CP1_process_nof"), any());
            verify(gateway).saveColumn(eq("CP1_process_rtf"), any());
        });
    }

    @Test
    void saveUsesReceivedAtAsTimeSeriesTimestamp() {
        contextRunner.run(context -> {
            ProcessTrackingResultRepositoryImpl repository = context.getBean(ProcessTrackingResultRepositoryImpl.class);
            Instant receivedAt = Instant.parse("2026-06-26T09:30:00Z");
            ProcessResult result = ProcessResult.builder()
                    .unitCode("CP1")
                    .trackingType(TrackingType.PROCESS)
                    .segmentCode("nof")
                    .receivedAt(receivedAt)
                    .generatedAt(Instant.parse("2026-06-26T09:30:05Z"))
                    .build();

            repository.save(Arrays.asList(result));

            ArgumentCaptor<TimeSeriesDataRequest> requestCaptor = ArgumentCaptor.forClass(TimeSeriesDataRequest.class);
            verify(gateway).saveColumn(eq("CP1_process_nof"), requestCaptor.capture());
            assertThat(requestCaptor.getValue().getTimestamp()).isEqualTo(receivedAt.toEpochMilli());
            assertThat(requestCaptor.getValue().getValues())
                    .allSatisfy(value -> assertThat(value.getT()).isEqualTo(receivedAt.toEpochMilli()));
        });
    }

    private PointConfig point(String name, PointDataType type) {
        return PointConfig.builder()
                .name(name)
                .type(type)
                .build();
    }
}
