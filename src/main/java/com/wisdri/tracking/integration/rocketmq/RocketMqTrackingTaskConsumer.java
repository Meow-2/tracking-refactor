package com.wisdri.tracking.integration.rocketmq;

import com.wisdri.tracking.application.tracking.TrackingTaskApplicationService;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * RocketMQ 跟踪任务消费适配器。
 */
@Component
public class RocketMqTrackingTaskConsumer {
    /**
     * 跟踪任务消息映射器。
     */
    @Resource
    private RocketMqTrackingTaskMapper mapper;

    /**
     * 跟踪任务应用服务。
     */
    @Resource
    private TrackingTaskApplicationService applicationService;

    /**
     * 消费 RocketMQ 消息体。
     */
    public void consume(String payload) {
        TrackingTask task = mapper.fromJson(payload);
        applicationService.handle(task);
    }
}
