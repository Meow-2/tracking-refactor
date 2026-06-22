package com.wisdri.tracking.infrastructure.service.rocketmq;

import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.properties.RocketMqConfig;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrackingTaskProducerTest {
    @Mock
    private RocketMQClientTemplate rocketMQClientTemplate;

    @Test
    void sendsFifoMessageWithUnitAndTrackingTypeOrderKey() {
        RocketMqConfig rocketMqConfig = new RocketMqConfig();
        rocketMqConfig.getProducer().setTopic("tracking-data-topic");
        TrackingTaskProducer producer = new TrackingTaskProducer();
        ReflectionTestUtils.setField(producer, "rocketMQClientTemplate", rocketMQClientTemplate);
        ReflectionTestUtils.setField(producer, "rocketMqConfig", rocketMqConfig);
        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();

        producer.send(task);

        verify(rocketMQClientTemplate).syncSendFifoMessage("tracking-data-topic", task, "CP1:process");
    }
}
