package com.wisdri.tracking.application;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.tracking.impl.MqttPointMessageApplicationServiceImpl;
import com.wisdri.tracking.application.tracking.impl.TrackingTaskApplicationServiceImpl;
import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.port.message.TrackingTaskPublisher;
import com.wisdri.tracking.domain.port.point.LastPointSnapshotRepository;
import com.wisdri.tracking.domain.port.storage.AbnormalDataStorage;
import com.wisdri.tracking.domain.port.storage.ProcessResultStorage;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataDetector;
import com.wisdri.tracking.domain.service.event.TrackingEventDetector;
import com.wisdri.tracking.domain.service.point.PointExtractor;
import com.wisdri.tracking.domain.service.process.ProcessTrackingAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationServiceBehaviorTest {

    @Test
    void mqttPointMessageServicePublishesTaskAndUpdatesLastSnapshot() {
        RecordingTrackingConfigCacheService configCache = new RecordingTrackingConfigCacheService();
        RecordingLastPointSnapshotRepository snapshotRepository = new RecordingLastPointSnapshotRepository();
        RecordingPointExtractor pointExtractor = new RecordingPointExtractor();
        RecordingTrackingTaskPublisher publisher = new RecordingTrackingTaskPublisher();
        MqttPointMessageApplicationServiceImpl service = new MqttPointMessageApplicationServiceImpl();
        ReflectionTestUtils.setField(service, "configCache", configCache);
        ReflectionTestUtils.setField(service, "lastPointSnapshotRepository", snapshotRepository);
        ReflectionTestUtils.setField(service, "pointExtractor", pointExtractor);
        ReflectionTestUtils.setField(service, "eventDetector", (TrackingEventDetector) (latest, previous, config) -> Collections.emptyList());
        ReflectionTestUtils.setField(service, "taskPublisher", publisher);

        service.handle("CP1", TrackingType.PROCESS, Collections.singletonMap("speed", 1.2));

        assertThat(publisher.task.getUnitCode()).isEqualTo("CP1");
        assertThat(publisher.task.getPreviousSnapshot()).isSameAs(snapshotRepository.previous);
        assertThat(snapshotRepository.saved).isSameAs(pointExtractor.latest);
    }

    @Test
    void trackingTaskApplicationServiceStoresAbnormalDataAndProcessResults() {
        RecordingAbnormalDataStorage abnormalStorage = new RecordingAbnormalDataStorage();
        RecordingProcessResultStorage processStorage = new RecordingProcessResultStorage();
        TrackingTaskApplicationServiceImpl service = new TrackingTaskApplicationServiceImpl();
        ReflectionTestUtils.setField(service, "configCache", new RecordingTrackingConfigCacheService());
        ReflectionTestUtils.setField(service, "abnormalDataDetector",
                (AbnormalDataDetector) (latest, previous, config) -> Collections.singletonList(AbnormalData.builder().pointCode("speed").build()));
        ReflectionTestUtils.setField(service, "abnormalDataStorage", abnormalStorage);
        ReflectionTestUtils.setField(service, "processTrackingAlgorithm",
                (ProcessTrackingAlgorithm) input -> Collections.singletonList(ProcessResult.builder()
                        .unitCode("CP1")
                        .trackingType(TrackingType.PROCESS)
                        .segmentName("SF段")
                        .headLength(new BigDecimal("100"))
                        .build()));
        ReflectionTestUtils.setField(service, "processResultStorage", processStorage);

        service.handle(TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .latestSnapshot(PointSnapshot.builder().build())
                .previousSnapshot(PointSnapshot.builder().build())
                .build());

        assertThat(abnormalStorage.saved).hasSize(1);
        assertThat(processStorage.saved).hasSize(1);
        assertThat(processStorage.saved.get(0).getSegmentName()).isEqualTo("SF段");
    }

    private static class RecordingTrackingConfigCacheService implements TrackingConfigCacheService {
        private final TrackingConfig config = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(Boolean.TRUE)
                .build();

        @Override
        public Optional<TrackingConfig> get(String unitCode, TrackingType trackingType) {
            return Optional.of(config);
        }

        @Override
        public void put(TrackingConfig config) {
        }

        @Override
        public void evict(String unitCode, TrackingType trackingType) {
        }

        @Override
        public void evictAll() {
        }
    }

    private static class RecordingLastPointSnapshotRepository implements LastPointSnapshotRepository {
        private final PointSnapshot previous = PointSnapshot.builder().receivedAt(Instant.parse("2026-06-17T08:00:00Z")).build();
        private PointSnapshot saved;

        @Override
        public Optional<PointSnapshot> find(String unitCode, TrackingType trackingType) {
            return Optional.of(previous);
        }

        @Override
        public void save(String unitCode, TrackingType trackingType, PointSnapshot snapshot) {
            this.saved = snapshot;
        }
    }

    private static class RecordingPointExtractor implements PointExtractor {
        private final PointSnapshot latest = PointSnapshot.builder()
                .values(Collections.singletonMap("speed", PointValue.of(1.2)))
                .receivedAt(Instant.parse("2026-06-17T08:01:00Z"))
                .build();

        @Override
        public PointSnapshot extract(Map<String, Object> rawValues, TrackingConfig config) {
            return latest;
        }
    }

    private static class RecordingTrackingTaskPublisher implements TrackingTaskPublisher {
        private TrackingTask task;

        @Override
        public void publish(TrackingTask task) {
            this.task = task;
        }
    }

    private static class RecordingAbnormalDataStorage implements AbnormalDataStorage {
        private List<AbnormalData> saved;

        @Override
        public void save(List<AbnormalData> abnormalData) {
            this.saved = abnormalData;
        }
    }

    private static class RecordingProcessResultStorage implements ProcessResultStorage {
        private List<ProcessResult> saved;

        @Override
        public void save(List<ProcessResult> results) {
            this.saved = results;
        }
    }
}
