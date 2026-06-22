package com.wisdri.tracking.infrastructure.service.rocketmq;

import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.infrastructure.properties.RocketMqConfig;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * RocketMQ 跟踪任务生产者。
 */
@Component
public class TrackingTaskProducer {
    /**
     * RocketMQ v5 Spring client template。
     */
    @Resource
    private RocketMQClientTemplate rocketMQClientTemplate;

    /**
     * RocketMQ 配置。
     */
    @Resource
    private RocketMqConfig rocketMqConfig;

    /**
     * 发送跟踪任务，同一机组同一跟踪类型保持 FIFO 顺序。
     * <p>
     * RocketMQ FIFO 消息按 orderKey 分组有序，因此这里使用 unitCode + trackingType
     * 作为顺序键，保证同一个 topic 对应的任务不会被并发乱序消费。
     */
    public void send(TrackingTask task) {
        rocketMQClientTemplate.syncSendFifoMessage(
                rocketMqConfig.getProducer().getTopic(),
                task,
                orderKey(task)
        );
    }

    /**
     * 构造 RocketMQ FIFO 顺序键。
     */
    private String orderKey(TrackingTask task) {
        return task.getUnitCode() + ":" + task.getTrackingType().getCode();
    }
}
