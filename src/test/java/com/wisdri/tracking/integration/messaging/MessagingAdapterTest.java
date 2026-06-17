package com.wisdri.tracking.integration.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.application.tracking.MqttPointMessageApplicationService;
import com.wisdri.tracking.application.tracking.TrackingTaskApplicationService;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.integration.mqtt.MqttRawMessageMapper;
import com.wisdri.tracking.integration.mqtt.MqttTrackingMessageHandler;
import com.wisdri.tracking.integration.rocketmq.RocketMqMessageGateway;
import com.wisdri.tracking.integration.rocketmq.RocketMqTrackingTaskConsumer;
import com.wisdri.tracking.integration.rocketmq.RocketMqTrackingTaskMapper;
import com.wisdri.tracking.integration.rocketmq.RocketMqTrackingTaskPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingAdapterTest {

    @Test
    void mqttRawMessageMapperConvertsPayloadToRawPointMap() {
        MqttRawMessageMapper mapper = mqttRawMessageMapper();

        Map<String, Object> values = mapper.toRawValues("{\"speed\":1.25,\"coil_no\":\"4605000400E\"}");

        assertThat(values).containsEntry("speed", 1.25);
        assertThat(values).containsEntry("coil_no", "4605000400E");
    }

    @Test
    void mqttTrackingMessageHandlerDelegatesRawValuesToApplicationService() {
        RecordingMqttPointMessageApplicationService applicationService = new RecordingMqttPointMessageApplicationService();
        MqttTrackingMessageHandler handler = new MqttTrackingMessageHandler();
        ReflectionTestUtils.setField(handler, "mapper", mqttRawMessageMapper());
        ReflectionTestUtils.setField(handler, "applicationService", applicationService);

        handler.handle("CP1", TrackingType.PROCESS, "{\"speed\":1.25}");

        assertThat(applicationService.unitCode).isEqualTo("CP1");
        assertThat(applicationService.trackingType).isEqualTo(TrackingType.PROCESS);
        assertThat(applicationService.rawValues).containsEntry("speed", 1.25);
    }

    @Test
    void rocketMqMapperSerializesAndDeserializesTrackingTask() {
        RocketMqTrackingTaskMapper mapper = rocketMqTrackingTaskMapper();
        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .publishedAt(Instant.parse("2026-06-17T08:30:16Z"))
                .build();

        String json = mapper.toJson(task);
        TrackingTask restored = mapper.fromJson(json);

        assertThat(restored.getUnitCode()).isEqualTo("CP1");
        assertThat(restored.getTrackingType()).isEqualTo(TrackingType.PROCESS);
        assertThat(restored.getPublishedAt()).isEqualTo(Instant.parse("2026-06-17T08:30:16Z"));
    }

    @Test
    void rocketMqPublisherAndConsumerAdaptTrackingTask() {
        RecordingRocketMqMessageGateway gateway = new RecordingRocketMqMessageGateway();
        RocketMqTrackingTaskMapper mapper = rocketMqTrackingTaskMapper();
        RocketMqTrackingTaskPublisher publisher = new RocketMqTrackingTaskPublisher();
        ReflectionTestUtils.setField(publisher, "gateway", gateway);
        ReflectionTestUtils.setField(publisher, "mapper", mapper);
        RecordingTrackingTaskApplicationService applicationService = new RecordingTrackingTaskApplicationService();
        RocketMqTrackingTaskConsumer consumer = new RocketMqTrackingTaskConsumer();
        ReflectionTestUtils.setField(consumer, "mapper", mapper);
        ReflectionTestUtils.setField(consumer, "applicationService", applicationService);
        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .publishedAt(Instant.parse("2026-06-17T08:30:16Z"))
                .build();

        publisher.publish(task);
        consumer.consume(gateway.payload);

        assertThat(gateway.payload).contains("\"unitCode\":\"CP1\"");
        assertThat(applicationService.task.getUnitCode()).isEqualTo("CP1");
        assertThat(applicationService.task.getTrackingType()).isEqualTo(TrackingType.PROCESS);
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static MqttRawMessageMapper mqttRawMessageMapper() {
        MqttRawMessageMapper mapper = new MqttRawMessageMapper();
        ReflectionTestUtils.setField(mapper, "objectMapper", objectMapper());
        return mapper;
    }

    private static RocketMqTrackingTaskMapper rocketMqTrackingTaskMapper() {
        RocketMqTrackingTaskMapper mapper = new RocketMqTrackingTaskMapper();
        ReflectionTestUtils.setField(mapper, "objectMapper", objectMapper());
        return mapper;
    }

    private static class RecordingMqttPointMessageApplicationService implements MqttPointMessageApplicationService {
        private String unitCode;
        private TrackingType trackingType;
        private Map<String, Object> rawValues;

        @Override
        public void handle(String unitCode, TrackingType trackingType, Map<String, Object> rawValues) {
            this.unitCode = unitCode;
            this.trackingType = trackingType;
            this.rawValues = rawValues;
        }
    }

    private static class RecordingRocketMqMessageGateway implements RocketMqMessageGateway {
        private String payload;

        @Override
        public void send(String payload) {
            this.payload = payload;
        }
    }

    private static class RecordingTrackingTaskApplicationService implements TrackingTaskApplicationService {
        private TrackingTask task;

        @Override
        public void handle(TrackingTask task) {
            this.task = task;
        }
    }
}
