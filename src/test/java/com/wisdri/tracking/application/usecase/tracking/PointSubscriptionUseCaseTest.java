package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.TemplateConfig;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
import com.wisdri.tracking.infrastructure.properties.MqttConfig;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointSubscriptionUseCaseTest {

    @Test
    @SuppressWarnings("unchecked")
    void refreshesConfigsRegistersSubscriptionsAndInitializesShearRuntimes() {
        TrackingRuntimeRepositoryDispatcher dispatcher = mock(TrackingRuntimeRepositoryDispatcher.class);
        ProcessTrackingConfig process = ProcessTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("baf1_process_tracking")
                .build();
        BatchTrackingConfig batch = BatchTrackingConfig.builder()
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
        ShearTrackingConfig shear = ShearTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.SHEAR)
                .enable(true)
                .mqttTopic("baf1_shear_tracking")
                .tracking(ShearTrackingSection.builder()
                        .uncoilerShearPoint(Arrays.asList(ShearPointConfig.builder()
                                .name("entry-cut")
                                .porTrCode("por-a")
                                .build()))
                        .coilerShearPoint(Arrays.asList(ShearPointConfig.builder()
                                .name("exit-cut")
                                .porTrCodes(Arrays.asList("tr-a", "tr-b"))
                                .build()))
                        .build())
                .build();
        when(dispatcher.findConfig("BAF1", TrackingType.PROCESS)).thenReturn(Optional.of(process));
        when(dispatcher.findConfig("BAF1", TrackingType.BATCH)).thenReturn(Optional.of(batch));
        when(dispatcher.findConfig("BAF1", TrackingType.SHEAR)).thenReturn(Optional.of(shear));

        ObjectProvider<MqttPahoMessageDrivenChannelAdapter> adapterProvider = mock(ObjectProvider.class);
        when(adapterProvider.getIfAvailable()).thenReturn(null);
        MqttSubscriptionRegistry registry = new MqttSubscriptionRegistry();
        ReflectionTestUtils.setField(registry, "mqttConfig", new MqttConfig());
        ReflectionTestUtils.setField(registry, "mqttAdapterProvider", adapterProvider);
        TrackingProperties properties = new TrackingProperties();
        properties.setUnit("BAF1");

        PointSubscriptionUseCase useCase = new PointSubscriptionUseCase();
        ReflectionTestUtils.setField(useCase, "trackingProperties", properties);
        ReflectionTestUtils.setField(useCase, "trackingRuntimeRepositoryDispatcher", dispatcher);
        ReflectionTestUtils.setField(useCase, "mqttSubscriptionRegistry", registry);

        useCase.start();

        verify(dispatcher).refreshConfig();
        TrackingSubscription processSubscription = registry.find("baf1_process_tracking")
                .orElseThrow(AssertionError::new);
        assertSame(process, processSubscription.getConfig());
        assertNull(processSubscription.getTemplateCode());
        assertBatchSubscription(registry, "baf1_batch_tracking_fb1", batch, "fb1");
        assertBatchSubscription(registry, "baf1_batch_tracking_fb2", batch, "fb2");
        assertSame(shear, registry.find("baf1_shear_tracking")
                .orElseThrow(AssertionError::new).getConfig());

        ArgumentCaptor<TrackingRuntime> runtimes = ArgumentCaptor.forClass(TrackingRuntime.class);
        verify(dispatcher, times(3)).saveRuntime(runtimes.capture());
        Set<String> porTrCodes = new HashSet<>();
        for (TrackingRuntime runtime : runtimes.getAllValues()) {
            porTrCodes.add(((ShearTrackingRuntime) runtime).getPorTrCode());
        }
        assertEquals(new HashSet<>(Arrays.asList("por-a", "tr-a", "tr-b")), porTrCodes);
    }

    private void assertBatchSubscription(MqttSubscriptionRegistry registry,
                                         String topic,
                                         BatchTrackingConfig config,
                                         String templateCode) {
        TrackingSubscription subscription = registry.find(topic).orElseThrow(AssertionError::new);
        assertSame(config, subscription.getConfig());
        assertEquals(templateCode, subscription.getTemplateCode());
    }
}
