package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskProducer;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(runtimeRepository.findRuntimeAs(
                "BAF1", TrackingType.STATUS, StatusTrackingRuntime.class))
                .thenReturn(Optional.of(statusRuntime()));
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
        assertThat(task.getStatusContext().getReceivedAt())
                .isEqualTo(Instant.parse("2026-07-17T08:00:01Z"));
        assertThat(task.getStatusContext().getStartConditionPointValue()).isEqualByComparingTo("1");
        assertThat(task.getStatusContext().getCandidates().get("U1").getColorNo()).isEqualTo("12");
        assertThat(task.getStatusContext().getCandidates().get("U1").getProductNo()).isEqualTo(3);
        assertThat(task.getStatusContext().getCandidates().get("U1").getMaxLength())
                .isEqualByComparingTo("90");
        assertThat(task.getStatusContext().getCurrent().get(DeviceSide.UNCOILER))
                .satisfies(current -> {
                    assertThat(current.getRunning()).isTrue();
                    assertThat(current.getDeviceCode()).isEqualTo("U1");
                    assertThat(current.getProductNo()).isEqualTo(3);
                    assertThat(current.getColorNo()).isEqualTo("12");
                    assertThat(current.getRemainingLength()).isEqualByComparingTo("88.5");
                    assertThat(current.getMaxLength()).isEqualByComparingTo("90");
                });
        assertThat(task.getStatusContext().getCurrent().get(DeviceSide.COILER).getCoilNo())
                .isEqualTo("C001");
        assertThat(task.getStatusContext().getCurrent().get(DeviceSide.COILER).getProductNo())
                .isEqualTo(4);
        verify(snapshotRepository).save("BAF1", TrackingType.BATCH, "fb1", task.getLatestSnapshot());
    }

    @Test
    void calculatesStatusOnProducerWithoutSendingStatusTask() {
        StatusTrackingConfig config = StatusTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.STATUS)
                .enable(true)
                .build();
        MqttSubscriptionRegistry registry = mock(MqttSubscriptionRegistry.class);
        TrackingRuntimeRepositoryDispatcher runtimeRepository = mock(TrackingRuntimeRepositoryDispatcher.class);
        LastPointSnapshotRepository snapshotRepository = mock(LastPointSnapshotRepository.class);
        TrackingTaskProducer taskProducer = mock(TrackingTaskProducer.class);
        TrackingAlgorithmDispatcher algorithmDispatcher = mock(TrackingAlgorithmDispatcher.class);
        when(registry.find("cp1_status_tracking"))
                .thenReturn(Optional.of(new TrackingSubscription(config, null)));
        when(runtimeRepository.findConfig("CP1", TrackingType.STATUS)).thenReturn(Optional.of(config));

        TrackingTaskProducerUseCase useCase = new TrackingTaskProducerUseCase();
        ReflectionTestUtils.setField(useCase, "mqttSubscriptionRegistry", registry);
        ReflectionTestUtils.setField(useCase, "runtimeRepositoryDispatcher", runtimeRepository);
        ReflectionTestUtils.setField(useCase, "lastPointSnapshotRepository", snapshotRepository);
        ReflectionTestUtils.setField(useCase, "trackingTaskProducer", taskProducer);
        ReflectionTestUtils.setField(useCase, "trackingAlgorithmDispatcher", algorithmDispatcher);

        useCase.handle("cp1_status_tracking", "{\"run\":1}");

        ArgumentCaptor<TrackingInput> inputCaptor = ArgumentCaptor.forClass(TrackingInput.class);
        verify(algorithmDispatcher).calculate(inputCaptor.capture());
        assertThat(inputCaptor.getValue().getTrackingType()).isEqualTo(TrackingType.STATUS);
        assertThat(inputCaptor.getValue().getLatestSnapshot().getValues()).containsEntry("run", 1);
        verify(taskProducer, never()).send(org.mockito.ArgumentMatchers.any());
    }

    private StatusTrackingRuntime statusRuntime() {
        Map<DeviceSide, StatusCurrentRuntime> current = new EnumMap<>(DeviceSide.class);
        current.put(DeviceSide.UNCOILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.UNCOILER)
                .running(true)
                .deviceCode("U1")
                .coilNo("U001")
                .productNo(3)
                .colorNo("12")
                .remainingLength(new BigDecimal("88.5"))
                .maxLength(new BigDecimal("90"))
                .build());
        current.put(DeviceSide.COILER, StatusCurrentRuntime.builder()
                .side(DeviceSide.COILER)
                .running(true)
                .coilNo("C001")
                .productNo(4)
                .build());
        return StatusTrackingRuntime.builder()
                .unitCode("BAF1")
                .trackingType(TrackingType.STATUS)
                .receivedAt(Instant.parse("2026-07-17T08:00:01Z"))
                .startConditionPointValue(BigDecimal.ONE)
                .candidates(Collections.singletonMap("U1", StatusCandidateRuntime.builder()
                        .coilNo("U001")
                        .productNo(3)
                        .colorNo("12")
                        .maxLength(new BigDecimal("90"))
                        .lengths(Arrays.asList(new BigDecimal("90"), new BigDecimal("88.5")))
                        .build()))
                .current(current)
                .build();
    }
}
