package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.application.usecase.tracking.TrackingTaskProducerUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MqttMessageListenerTest {
    @Mock
    private TrackingTaskProducerUseCase trackingTaskProducerUseCase;

    @Test
    void delegatesNormalizedTopicAndPayloadToUseCase() {
        MqttMessageListener listener = new MqttMessageListener();
        ReflectionTestUtils.setField(listener, "trackingTaskProducerUseCase", trackingTaskProducerUseCase);

        listener.handle("cp1/process", "{\"/line/speed\":1.25}");

        verify(trackingTaskProducerUseCase).handle("cp1/process", "{\"/line/speed\":1.25}");
    }
}
