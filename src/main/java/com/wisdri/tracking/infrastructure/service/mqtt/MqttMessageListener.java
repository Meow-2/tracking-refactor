package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.application.usecase.tracking.TrackingTaskProducerUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 通用 MQTT 跟踪消息入口。
 * <p>
 * 该类只承接 Spring Integration MQTT 消息，提取 topic 和 payload 后交给应用用例。
 */
@Slf4j
@Component
public class MqttMessageListener {
    /**
     * 跟踪任务生产用例。
     */
    @Resource
    private TrackingTaskProducerUseCase trackingTaskProducerUseCase;

    /**
     * Spring Integration MQTT 消息入口。
     * <p>
     * adapter 输出的消息会进入 trackingMqttInputChannel，再由该方法统一接收。
     * MQTT topic 可能位于不同 header，payload 也可能是 byte[] 或 String，
     * 因此先归一化为 handle(String, String) 所需的简单参数。
     */
    @ServiceActivator(inputChannel = "trackingMqttInputChannel")
    public void handle(Message<?> message) {
        String topic = topic(message);
        String payload = payload(message.getPayload());
        trackingTaskProducerUseCase.handle(topic, payload);
    }

    /**
     * 从 Spring Integration 消息头中提取 MQTT topic。
     */
    private String topic(Message<?> message) {
        Object topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        if (topic == null) {
            topic = message.getHeaders().get(MqttHeaders.TOPIC);
        }
        return topic == null ? null : String.valueOf(topic);
    }

    /**
     * 将 MQTT payload 归一化为字符串。
     */
    private String payload(Object payload) {
        if (payload instanceof byte[]) {
            return new String((byte[]) payload, StandardCharsets.UTF_8);
        }
        return payload == null ? null : String.valueOf(payload);
    }
}
