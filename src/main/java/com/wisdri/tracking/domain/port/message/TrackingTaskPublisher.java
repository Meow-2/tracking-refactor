package com.wisdri.tracking.domain.port.message;

import com.wisdri.tracking.domain.model.tracking.TrackingTask;

/**
 * 跟踪任务发布端口。
 *
 * <p>用于把接收侧整理好的业务任务发送给后续处理流程，具体可以由 RocketMQ 实现。</p>
 */
public interface TrackingTaskPublisher {
    /**
     * 发布一次跟踪处理任务。
     */
    void publish(TrackingTask task);
}
