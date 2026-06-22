package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.properties.MqttConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MqttSubscriptionRegistryLookupTest {
    @Test
    void resolvesTrackingConfigByTopic() {
        MqttSubscriptionRegistry registry = registryWithoutAdapter();
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("cp1/process")
                .build();

        registry.register(config);

        Optional<ProcessTrackingConfig> resolved = registry.findAs("cp1/process", ProcessTrackingConfig.class);
        assertThat(resolved).contains(config);
    }

    @SuppressWarnings("unchecked")
    private MqttSubscriptionRegistry registryWithoutAdapter() {
        ObjectProvider adapterProvider = mock(ObjectProvider.class);
        MqttSubscriptionRegistry registry = new MqttSubscriptionRegistry();
        ReflectionTestUtils.setField(registry, "mqttConfig", new MqttConfig());
        ReflectionTestUtils.setField(registry, "mqttAdapterProvider", adapterProvider);
        return registry;
    }
}
