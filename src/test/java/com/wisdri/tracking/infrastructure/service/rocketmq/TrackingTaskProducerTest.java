package com.wisdri.tracking.infrastructure.service.rocketmq;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingTaskProducerTest {
    @Test
    void includesTemplateCodeInBatchOrderKeyAndAlgorithmInput() {
        TrackingTask task = TrackingTask.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .templateCode("fb1")
                .build();
        TrackingTaskProducer producer = new TrackingTaskProducer();

        String orderKey = ReflectionTestUtils.invokeMethod(producer, "orderKey", task);

        assertThat(orderKey).isEqualTo("BAF1:batch:fb1");
        assertThat(TrackingInput.of(task).getTemplateCode()).isEqualTo("fb1");
    }

    @Test
    void keepsOriginalOrderKeyWithoutTemplateCode() {
        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();
        TrackingTaskProducer producer = new TrackingTaskProducer();

        String orderKey = ReflectionTestUtils.invokeMethod(producer, "orderKey", task);

        assertThat(orderKey).isEqualTo("CP1:process");
    }
}
