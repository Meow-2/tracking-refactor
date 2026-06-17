package com.wisdri.tracking.domain;

import com.wisdri.tracking.domain.model.config.RollingConfig;
import com.wisdri.tracking.domain.model.config.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.TrackingSection;
import com.wisdri.tracking.domain.model.config.LengthMode;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.port.config.TrackingConfigRepository;
import com.wisdri.tracking.domain.port.message.TrackingTaskPublisher;
import com.wisdri.tracking.domain.port.point.LastPointSnapshotRepository;
import com.wisdri.tracking.domain.port.storage.ProcessResultStorage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DomainContractTest {

    @Test
    void trackingConfigModelsProcessConfigurationWithoutMiddlewareTypes() {
        TrackingPointGroup group = TrackingPointGroup.builder()
                .lengthPoints(Arrays.asList("group1_length_1", "group1_length_2"))
                .coilNoPoint("group1_coil_no")
                .rollingCoiler(Boolean.TRUE)
                .build();
        SegmentConfig segment = SegmentConfig.builder()
                .name("SF段")
                .pointPrefix("/aygg_tracking/cp1/process/tech/sf/")
                .lengthCorrect(new BigDecimal("-50"))
                .lengthArrayIndex(0)
                .points(Arrays.asList("sf_plate_temp", "sf_furnace_pressure"))
                .build();
        TrackingConfig config = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(Boolean.TRUE)
                .mqttTopic("cp1_process_tracking")
                .tracking(TrackingSection.builder()
                        .pointPrefix("/aygg_tracking/cp1/process/tracking/")
                        .speedPoint("center_speed_pv")
                        .startCondition(StartCondition.builder()
                                .point("center_speed_pv")
                                .threshold(new BigDecimal("0.7"))
                                .build())
                        .lengthMode(LengthMode.WELDER)
                        .rolling(RollingConfig.builder()
                                .directPoint("rolling_direction")
                                .passNoPoint("pass_no_pv")
                                .directReverse(Boolean.FALSE)
                                .build())
                        .points(Collections.singletonList(group))
                        .build())
                .segments(Collections.singletonList(segment))
                .build();

        assertThat(config.getUnitCode()).isEqualTo("CP1");
        assertThat(config.getTracking().getLengthMode()).isEqualTo(LengthMode.WELDER);
        assertThat(config.getSegments()).extracting(SegmentConfig::getName).containsExactly("SF段");
    }

    @Test
    void pointSnapshotExposesPointValuesAndReceiveTime() {
        Map<String, PointValue> values = new LinkedHashMap<>();
        values.put("coil_no", PointValue.of("4605000400E"));
        values.put("speed", PointValue.of(new BigDecimal("1.25")));
        values.put("rolling_direction", PointValue.of("1"));

        PointSnapshot snapshot = PointSnapshot.builder()
                .values(values)
                .receivedAt(Instant.parse("2026-06-17T08:30:15.123Z"))
                .build();

        assertThat(snapshot.value("coil_no").map(PointValue::stringValue)).contains("4605000400E");
        assertThat(snapshot.value("speed").map(PointValue::decimalValue)).contains(new BigDecimal("1.25"));
        assertThat(snapshot.value("rolling_direction").map(PointValue::booleanValue)).contains(Boolean.TRUE);
        assertThat(snapshot.getReceivedAt()).isEqualTo(Instant.parse("2026-06-17T08:30:15.123Z"));
    }

    @Test
    void trackingTaskCarriesLatestAndPreviousSnapshotWithoutConfigSnapshot() {
        TrackingConfig config = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(Boolean.TRUE)
                .mqttTopic("cp1_process_tracking")
                .build();
        PointSnapshot previous = PointSnapshot.builder().receivedAt(Instant.parse("2026-06-17T08:29:15Z")).build();
        PointSnapshot latest = PointSnapshot.builder().receivedAt(Instant.parse("2026-06-17T08:30:15Z")).build();

        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .previousSnapshot(previous)
                .latestSnapshot(latest)
                .publishedAt(Instant.parse("2026-06-17T08:30:16Z"))
                .build();
        TrackingInput input = TrackingInput.of(task, config);

        assertThat(input.getConfig()).isSameAs(config);
        assertThat(input.getPreviousSnapshot()).isSameAs(previous);
        assertThat(input.getLatestSnapshot()).isSameAs(latest);
    }

    @Test
    void portsDescribeDomainCapabilitiesWithoutNamingRedisOrRocketMq() {
        InMemoryConfigRepository configRepository = new InMemoryConfigRepository();
        InMemoryLastPointSnapshotRepository snapshotRepository = new InMemoryLastPointSnapshotRepository();
        RecordingTaskPublisher publisher = new RecordingTaskPublisher();
        RecordingResultStorage storage = new RecordingResultStorage();

        TrackingConfig config = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .mqttTopic("cp1_process_tracking")
                .build();
        PointSnapshot snapshot = PointSnapshot.builder().receivedAt(Instant.parse("2026-06-17T08:30:15Z")).build();
        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .latestSnapshot(snapshot)
                .publishedAt(Instant.parse("2026-06-17T08:30:16Z"))
                .build();
        ProcessResult result = ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .segmentName("SF段")
                .coilNo("4605000400E")
                .headLength(new BigDecimal("120.5"))
                .speed(new BigDecimal("1.2"))
                .parameters(Collections.singletonMap("sf_plate_temp", 780))
                .generatedAt(Instant.parse("2026-06-17T08:30:16Z"))
                .build();

        configRepository.save(config);
        snapshotRepository.save("CP1", TrackingType.PROCESS, snapshot);
        publisher.publish(task);
        storage.save(Collections.singletonList(result));

        assertThat(configRepository.find("CP1", TrackingType.PROCESS)).contains(config);
        assertThat(snapshotRepository.find("CP1", TrackingType.PROCESS)).contains(snapshot);
        assertThat(publisher.published).containsExactly(task);
        assertThat(storage.saved).containsExactly(result);
    }

    private static class InMemoryConfigRepository implements TrackingConfigRepository {
        private TrackingConfig config;

        @Override
        public Optional<TrackingConfig> find(String unitCode, TrackingType trackingType) {
            if (config != null && unitCode.equals(config.getUnitCode()) && trackingType == config.getTrackingType()) {
                return Optional.of(config);
            }
            return Optional.empty();
        }

        @Override
        public void save(TrackingConfig config) {
            this.config = config;
        }
    }

    private static class InMemoryLastPointSnapshotRepository implements LastPointSnapshotRepository {
        private PointSnapshot snapshot;

        @Override
        public Optional<PointSnapshot> find(String unitCode, TrackingType trackingType) {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public void save(String unitCode, TrackingType trackingType, PointSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    private static class RecordingTaskPublisher implements TrackingTaskPublisher {
        private final List<TrackingTask> published = new java.util.ArrayList<>();

        @Override
        public void publish(TrackingTask task) {
            published.add(task);
        }
    }

    private static class RecordingResultStorage implements ProcessResultStorage {
        private final List<ProcessResult> saved = new java.util.ArrayList<>();

        @Override
        public void save(List<ProcessResult> results) {
            saved.addAll(results);
        }
    }
}
