package com.wisdri.tracking.application;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.config.impl.TrackingConfigRefreshServiceImpl;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.port.config.TrackingConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingConfigRefreshServiceTest {

    @Test
    void refreshReadsConfigFromRepositoryAndUpdatesCache() {
        RecordingTrackingConfigCacheService cache = new RecordingTrackingConfigCacheService();
        TrackingConfigRefreshServiceImpl service = new TrackingConfigRefreshServiceImpl();
        ReflectionTestUtils.setField(service, "configRepository", new RecordingConfigRepository());
        ReflectionTestUtils.setField(service, "configCache", cache);

        Optional<TrackingConfig> config = service.refresh("CP1", TrackingType.PROCESS);

        assertThat(config).containsSame(cache.config);
        assertThat(cache.evicted).isFalse();
    }

    @Test
    void refreshEvictsCacheWhenRepositoryConfigMissing() {
        RecordingTrackingConfigCacheService cache = new RecordingTrackingConfigCacheService();
        TrackingConfigRefreshServiceImpl service = new TrackingConfigRefreshServiceImpl();
        ReflectionTestUtils.setField(service, "configRepository", new EmptyConfigRepository());
        ReflectionTestUtils.setField(service, "configCache", cache);

        Optional<TrackingConfig> config = service.refresh("CP1", TrackingType.PROCESS);

        assertThat(config).isEmpty();
        assertThat(cache.evicted).isTrue();
    }

    private static class RecordingConfigRepository implements TrackingConfigRepository {
        private final TrackingConfig config = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .mqttTopic("cp1_process_tracking")
                .build();

        @Override
        public Optional<TrackingConfig> find(String unitCode, TrackingType trackingType) {
            return Optional.of(config);
        }

        @Override
        public void save(TrackingConfig config) {
        }
    }

    private static class EmptyConfigRepository implements TrackingConfigRepository {
        @Override
        public Optional<TrackingConfig> find(String unitCode, TrackingType trackingType) {
            return Optional.empty();
        }

        @Override
        public void save(TrackingConfig config) {
        }
    }

    private static class RecordingTrackingConfigCacheService implements TrackingConfigCacheService {
        private TrackingConfig config;
        private boolean evicted;

        @Override
        public Optional<TrackingConfig> get(String unitCode, TrackingType trackingType) {
            return Optional.ofNullable(config);
        }

        @Override
        public void put(TrackingConfig config) {
            this.config = config;
        }

        @Override
        public void evict(String unitCode, TrackingType trackingType) {
            this.evicted = true;
        }

        @Override
        public void evictAll() {
        }
    }
}
