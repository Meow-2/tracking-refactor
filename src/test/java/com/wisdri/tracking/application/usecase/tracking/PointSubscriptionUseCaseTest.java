package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointSubscriptionUseCaseTest {
    @Mock
    private TrackingConfigRepository trackingConfigRepository;

    @Mock
    private MqttSubscriptionRegistry mqttSubscriptionRegistry;

    @Test
    void startsByRefreshingConfigAndRegisteringCurrentUnitTrackingSubscriptions() {
        TrackingProperties trackingProperties = new TrackingProperties();
        trackingProperties.setUnit("CP1");
        PointSubscriptionUseCase useCase = new PointSubscriptionUseCase();
        ReflectionTestUtils.setField(useCase, "trackingProperties", trackingProperties);
        ReflectionTestUtils.setField(useCase, "trackingConfigRepository", trackingConfigRepository);
        ReflectionTestUtils.setField(useCase, "mqttSubscriptionRegistry", mqttSubscriptionRegistry);
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("cp1/process")
                .build();
        when(trackingConfigRepository.find("CP1", TrackingType.PROCESS)).thenReturn(Optional.of(config));

        useCase.start();

        verify(trackingConfigRepository).refresh();
        verify(mqttSubscriptionRegistry).register(config);
    }
}
