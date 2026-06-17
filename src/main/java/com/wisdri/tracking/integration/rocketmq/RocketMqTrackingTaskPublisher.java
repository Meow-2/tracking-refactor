package com.wisdri.tracking.integration.rocketmq;

import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.repository.tracking.TrackingTaskPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 基于 RocketMQ 的跟踪任务发布适配器。
 */
@Component
public class RocketMqTrackingTaskPublisher implements TrackingTaskPublisher {
    /**
     * RocketMQ 发送网关。
     */
    @Resource
    private RocketMqMessageGateway gateway;

    /**
     * 跟踪任务消息映射器。
     */
    @Resource
    private RocketMqTrackingTaskMapper mapper;

    /**
     * 发布跟踪任务消息。
     */
    @Override
    public void publish(TrackingTask task) {
        gateway.send(mapper.toJson(task));
    }
}
