package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.TemplateConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.properties.MqttConfig;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqttSubscriptionRegistryTest {
    private MqttSubscriptionRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<MqttPahoMessageDrivenChannelAdapter> adapterProvider = mock(ObjectProvider.class);
        when(adapterProvider.getIfAvailable()).thenReturn(null);
        registry = new MqttSubscriptionRegistry();
        ReflectionTestUtils.setField(registry, "mqttConfig", new MqttConfig());
        ReflectionTestUtils.setField(registry, "mqttAdapterProvider", adapterProvider);
    }

    @Test
    void expandsBatchTemplateIntoConcreteTopics() {
        BatchTrackingConfig config = BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .enable(true)
                .mqttTopic("baf1_batch_tracking_{template}")
                .template(TemplateConfig.builder()
                        .code("fb{index}")
                        .indexFrom(1)
                        .indexTo(2)
                        .build())
                .build();

        registry.register(config);

        TrackingSubscription fb1 = registry.find("baf1_batch_tracking_fb1")
                .orElseThrow(AssertionError::new);
        TrackingSubscription fb2 = registry.find("baf1_batch_tracking_fb2")
                .orElseThrow(AssertionError::new);
        assertThat(fb1.getConfig()).isSameAs(config);
        assertThat(fb1.getTemplateCode()).isEqualTo("fb1");
        assertThat(fb2.getTemplateCode()).isEqualTo("fb2");
        assertThat(registry.find("baf1_batch_tracking_{template}")).isEmpty();
    }

    @Test
    void keepsNonTemplateSubscriptionWithoutTemplateCode() {
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("cp1_process_tracking")
                .build();

        registry.register(config);

        TrackingSubscription subscription = registry.find("cp1_process_tracking")
                .orElseThrow(AssertionError::new);
        assertThat(subscription.getConfig()).isSameAs(config);
        assertThat(subscription.getTemplateCode()).isNull();
    }

    @Test
    void registersStatusAsOrdinaryNonTemplateSubscription() {
        StatusTrackingConfig config = StatusTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .enable(true)
                .mqttTopic("cp1_status_tracking")
                .build();

        registry.register(config);

        TrackingSubscription subscription = registry.find("cp1_status_tracking")
                .orElseThrow(AssertionError::new);
        assertThat(subscription.getConfig()).isSameAs(config);
        assertThat(subscription.getTemplateCode()).isNull();
    }
}
