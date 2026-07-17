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
     * RocketMQ FIFO 消息按 orderKey 分组有序。模板化跟踪额外使用 templateCode，
     * 保证单模板实例有序，同时允许不同模板实例并行消费。
     */
    public void send(TrackingTask task) {
        rocketMQClientTemplate.syncSendFifoMessage(
                destination(task),
                task,
                orderKey(task)
        );
    }

    /**
     * 使用机组代码作为 Tag，使各机组消费者只接收自己的任务。
     */
    private String destination(TrackingTask task) {
        return rocketMqConfig.getProducer().getTopic() + ":" + task.getUnitCode();
    }

    /**
     * 构造 RocketMQ FIFO 顺序键。
     */
    private String orderKey(TrackingTask task) {
        String orderKey = task.getUnitCode() + ":" + task.getTrackingType().getCode();
        if (task.getTemplateCode() == null || task.getTemplateCode().trim().isEmpty()) {
            return orderKey;
        }
        return orderKey + ":" + task.getTemplateCode();
    }
}
