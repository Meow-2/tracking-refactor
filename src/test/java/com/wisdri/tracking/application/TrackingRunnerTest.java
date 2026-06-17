package com.wisdri.tracking.application;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.runner.TrackingRunner;
import com.wisdri.tracking.application.tracking.TrackingWorkerManager;
import com.wisdri.tracking.common.config.TrackingProperties;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.port.config.TrackingConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingRunnerTest {

    @Test
    void runnerStartsEnabledTrackingWorkersForCurrentUnit() throws Exception {
        TrackingProperties properties = new TrackingProperties();
        properties.setUnit("CP1");
        RecordingTrackingConfigCacheService configCache = new RecordingTrackingConfigCacheService();
        RecordingWorkerManager workerManager = new RecordingWorkerManager();
        TrackingRunner runner = new TrackingRunner();
        ReflectionTestUtils.setField(runner, "trackingProperties", properties);
        ReflectionTestUtils.setField(runner, "configRepository", new RecordingConfigRepository());
        ReflectionTestUtils.setField(runner, "configCache", configCache);
        ReflectionTestUtils.setField(runner, "workerManager", workerManager);

        runner.run(null);

        assertThat(configCache.cached).hasSize(1);
        assertThat(workerManager.started).containsExactly("CP1:PROCESS:cp1_process_tracking");
    }

    private static class RecordingTrackingConfigCacheService implements TrackingConfigCacheService {
        private final List<TrackingConfig> cached = new ArrayList<>();

        @Override
        public Optional<TrackingConfig> get(String unitCode, TrackingType trackingType) {
            return cached.stream()
                    .filter(config -> unitCode.equals(config.getUnitCode()) && trackingType == config.getTrackingType())
                    .findFirst();
        }

        @Override
        public void put(TrackingConfig config) {
            cached.add(config);
        }

        @Override
        public void evict(String unitCode, TrackingType trackingType) {
        }

        @Override
        public void evictAll() {
        }
    }

    private static class RecordingConfigRepository implements TrackingConfigRepository {
        @Override
        public Optional<TrackingConfig> find(String unitCode, TrackingType trackingType) {
            if (TrackingType.PROCESS == trackingType) {
                return Optional.of(TrackingConfig.builder()
                        .unitCode(unitCode)
                        .trackingType(trackingType)
                        .enable(Boolean.TRUE)
                        .mqttTopic("cp1_process_tracking")
                        .build());
            }
            return Optional.empty();
        }

        @Override
        public void save(TrackingConfig config) {
        }
    }

    private static class RecordingWorkerManager implements TrackingWorkerManager {
        private final List<String> started = new ArrayList<>();

        @Override
        public void start(String unitCode, TrackingType trackingType, String mqttTopic) {
            started.add(unitCode + ":" + trackingType.name() + ":" + mqttTopic);
        }

        @Override
        public void stop(String unitCode, TrackingType trackingType) {
        }
    }
}
