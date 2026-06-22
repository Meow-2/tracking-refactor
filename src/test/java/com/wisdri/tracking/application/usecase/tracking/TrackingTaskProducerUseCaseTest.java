package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.infrastructure.properties.MqttConfig;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingTaskProducerUseCaseTest {
    @Mock
    private TrackingConfigRepository configRepository;

    @Mock
    private LastPointSnapshotRepository lastPointSnapshotRepository;

    @Mock
    private TrackingTaskProducer trackingTaskProducer;

    @Test
    void producesTaskByResolvedTopicSubscription() {
        MqttSubscriptionRegistry subscriptionRegistry = subscriptionRegistry();
        subscriptionRegistry.register(ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("cp1/process")
                .build());
        TrackingTaskProducerUseCase useCase = useCase(subscriptionRegistry);
        PointSnapshot previous = PointSnapshot.builder()
                .values(Collections.singletonMap("/line/speed", 1))
                .receivedAt(Instant.parse("2026-06-21T01:00:00Z"))
                .build();
        when(configRepository.find("CP1", TrackingType.PROCESS)).thenReturn(Optional.of(
                ProcessTrackingConfig.builder()
                        .unitCode("CP1")
                        .trackingType(TrackingType.PROCESS)
                        .enable(true)
                        .build()
        ));
        when(lastPointSnapshotRepository.find("CP1", TrackingType.PROCESS)).thenReturn(Optional.of(previous));

        useCase.handle("cp1/process", "{\"/line/speed\":1.25}");

        ArgumentCaptor<TrackingTask> taskCaptor = ArgumentCaptor.forClass(TrackingTask.class);
        verify(trackingTaskProducer).send(taskCaptor.capture());
        TrackingTask task = taskCaptor.getValue();
        assertThat(task.getUnitCode()).isEqualTo("CP1");
        assertThat(task.getTrackingType()).isEqualTo(TrackingType.PROCESS);
        assertThat(task.getPreviousSnapshot()).isSameAs(previous);
        assertThat(task.getPublishedAt()).isNotNull();
        assertThat(task.getLatestSnapshot().getReceivedAt()).isNotNull();
        assertThat(task.getLatestSnapshot().getValues()).containsEntry("/line/speed", new BigDecimal("1.25"));
        verify(lastPointSnapshotRepository).save("CP1", TrackingType.PROCESS, task.getLatestSnapshot());
    }

    @Test
    void ignoresUnregisteredTopic() {
        TrackingTaskProducerUseCase useCase = useCase(subscriptionRegistry());

        useCase.handle("cp1/process", "{}");

        verifyNoInteractions(configRepository, lastPointSnapshotRepository, trackingTaskProducer);
    }

    @Test
    void ignoresDisabledTrackingConfig() {
        MqttSubscriptionRegistry subscriptionRegistry = subscriptionRegistry();
        subscriptionRegistry.register(ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(true)
                .mqttTopic("cp1/process")
                .build());
        TrackingTaskProducerUseCase useCase = useCase(subscriptionRegistry);
        when(configRepository.find("CP1", TrackingType.PROCESS)).thenReturn(Optional.of(
                ProcessTrackingConfig.builder()
                        .unitCode("CP1")
                        .trackingType(TrackingType.PROCESS)
                        .enable(false)
                        .build()
        ));

        useCase.handle("cp1/process", "{}");

        verifyNoInteractions(lastPointSnapshotRepository, trackingTaskProducer);
    }

    private TrackingTaskProducerUseCase useCase(MqttSubscriptionRegistry subscriptionRegistry) {
        TrackingTaskProducerUseCase useCase = new TrackingTaskProducerUseCase();
        ReflectionTestUtils.setField(useCase, "mqttSubscriptionRegistry", subscriptionRegistry);
        ReflectionTestUtils.setField(useCase, "configRepository", configRepository);
        ReflectionTestUtils.setField(useCase, "lastPointSnapshotRepository", lastPointSnapshotRepository);
        ReflectionTestUtils.setField(useCase, "trackingTaskProducer", trackingTaskProducer);
        return useCase;
    }

    @SuppressWarnings("unchecked")
    private MqttSubscriptionRegistry subscriptionRegistry() {
        org.springframework.beans.factory.ObjectProvider adapterProvider = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        MqttSubscriptionRegistry registry = new MqttSubscriptionRegistry();
        ReflectionTestUtils.setField(registry, "mqttConfig", new MqttConfig());
        ReflectionTestUtils.setField(registry, "mqttAdapterProvider", adapterProvider);
        return registry;
    }
}
