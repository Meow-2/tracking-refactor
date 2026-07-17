package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskProducer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingTaskProducerUseCaseTest {
    @Test
    void propagatesSubscriptionTemplateCodeIntoTrackingTask() {
        BatchTrackingConfig config = BatchTrackingConfig.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.BATCH)
                .enable(true)
                .build();
        PointSnapshot previous = PointSnapshot.builder()
                .values(Collections.singletonMap("frame", "previous"))
                .receivedAt(Instant.parse("2026-07-17T08:00:00Z"))
                .build();
        MqttSubscriptionRegistry registry = mock(MqttSubscriptionRegistry.class);
        TrackingRuntimeRepositoryDispatcher runtimeRepository = mock(TrackingRuntimeRepositoryDispatcher.class);
        LastPointSnapshotRepository snapshotRepository = mock(LastPointSnapshotRepository.class);
        TrackingTaskProducer taskProducer = mock(TrackingTaskProducer.class);
        when(registry.find("baf1_batch_tracking_fb1"))
                .thenReturn(Optional.of(new TrackingSubscription(config, "fb1")));
        when(runtimeRepository.findConfig("BAF1", TrackingType.BATCH)).thenReturn(Optional.of(config));
        when(snapshotRepository.find("BAF1", TrackingType.BATCH, "fb1")).thenReturn(Optional.of(previous));

        TrackingTaskProducerUseCase useCase = new TrackingTaskProducerUseCase();
        ReflectionTestUtils.setField(useCase, "mqttSubscriptionRegistry", registry);
        ReflectionTestUtils.setField(useCase, "runtimeRepositoryDispatcher", runtimeRepository);
        ReflectionTestUtils.setField(useCase, "lastPointSnapshotRepository", snapshotRepository);
        ReflectionTestUtils.setField(useCase, "trackingTaskProducer", taskProducer);

        useCase.handle("baf1_batch_tracking_fb1", "{\"frame\":\"latest\"}");

        ArgumentCaptor<TrackingTask> taskCaptor = ArgumentCaptor.forClass(TrackingTask.class);
        verify(taskProducer).send(taskCaptor.capture());
        TrackingTask task = taskCaptor.getValue();
        assertThat(task.getTemplateCode()).isEqualTo("fb1");
        assertThat(task.getPreviousSnapshot()).isSameAs(previous);
        assertThat(task.getLatestSnapshot().getValues()).containsEntry("frame", "latest");
        verify(snapshotRepository).save("BAF1", TrackingType.BATCH, "fb1", task.getLatestSnapshot());
    }
}
