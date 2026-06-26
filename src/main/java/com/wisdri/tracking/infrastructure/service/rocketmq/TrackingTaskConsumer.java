package com.wisdri.tracking.infrastructure.service.rocketmq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wisdri.tracking.application.usecase.tracking.TrackingTaskConsumerUseCase;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 跟踪任务消费者。
 * <p>
 * 该类只负责 RocketMQ 消费协议适配：读取消息体、反序列化 TrackingTask，
 * 并把任务交给应用用例。
 */
@Slf4j
@Component
public class TrackingTaskConsumer implements RocketMQListener {
    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapperBuilder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /**
     * 跟踪任务消费用例。
     */
    @Resource
    private TrackingTaskConsumerUseCase trackingTaskConsumerUseCase;

    /**
     * RocketMQ 消费入口。
     */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            TrackingTask task = objectMapper.readValue(payload(messageView), TrackingTask.class);
            trackingTaskConsumerUseCase.consume(task);
            return ConsumeResult.SUCCESS;
        } catch (RuntimeException | IOException e) {
            log.error("消费 RocketMQ 跟踪任务失败", e);
            return ConsumeResult.FAILURE;
        }
    }

    private String payload(MessageView messageView) {
        ByteBuffer buffer = messageView.getBody().asReadOnlyBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
