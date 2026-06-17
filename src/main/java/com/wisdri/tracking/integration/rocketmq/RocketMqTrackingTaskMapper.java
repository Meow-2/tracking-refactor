package com.wisdri.tracking.integration.rocketmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * RocketMQ 跟踪任务消息映射器。
 *
 * <p>负责 TrackingTask 与 RocketMQ JSON 消息之间转换。</p>
 */
@Component
public class RocketMqTrackingTaskMapper {
    /**
     * JSON 序列化工具。
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 将跟踪任务序列化为 RocketMQ 消息体。
     */
    public String toJson(TrackingTask task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("RocketMQ 跟踪任务 JSON 序列化失败", e);
        }
    }

    /**
     * 将 RocketMQ 消息体反序列化为跟踪任务。
     */
    public TrackingTask fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, TrackingTask.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("RocketMQ 跟踪任务 JSON 解析失败", e);
        }
    }
}
