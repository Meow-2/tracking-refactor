package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.properties.MqttConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttSubscriptionRegistryTest {
    @Test
    void registersEnabledConfigToRegistryAndMqttAdapter() {
        MqttPahoMessageDrivenChannelAdapter adapter = mock(MqttPahoMessageDrivenChannelAdapter.class);
        ObjectProvider<MqttPahoMessageDrivenChannelAdapter> adapterProvider = adapterProvider(adapter);
        MqttConfig mqttConfig = new MqttConfig();
        mqttConfig.setQos(1);
        MqttSubscriptionRegistry registry = new MqttSubscriptionRegistry();
        ReflectionTestUtils.setField(registry, "mqttConfig", mqttConfig);
        ReflectionTestUtils.setField(registry, "mqttAdapterProvider", adapterProvider);
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("cp1/process")
                .build();

        registry.register(config);

        verify(adapter).addTopic("cp1/process", 1);
        org.assertj.core.api.Assertions.assertThat(registry.find("cp1/process")).contains(config);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<MqttPahoMessageDrivenChannelAdapter> adapterProvider(MqttPahoMessageDrivenChannelAdapter adapter) {
        ObjectProvider<MqttPahoMessageDrivenChannelAdapter> adapterProvider = mock(ObjectProvider.class);
        when(adapterProvider.getIfAvailable()).thenReturn(adapter);
        return adapterProvider;
    }
}
