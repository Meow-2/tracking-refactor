package com.wisdri.tracking.application;

import com.wisdri.tracking.application.config.impl.TrackingConfigCacheServiceImpl;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingConfigCacheServiceTest {

    @Test
    void cacheStoresAndReplacesConfigByUnitAndTrackingType() {
        TrackingConfigCacheServiceImpl cache = new TrackingConfigCacheServiceImpl();
        TrackingConfig first = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .mqttTopic("old_topic")
                .build();
        TrackingConfig refreshed = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .mqttTopic("new_topic")
                .build();

        cache.put(first);
        cache.put(refreshed);

        assertThat(cache.get("CP1", TrackingType.PROCESS)).containsSame(refreshed);
    }
}
