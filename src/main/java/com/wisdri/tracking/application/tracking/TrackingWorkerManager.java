package com.wisdri.tracking.application.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingType;

/**
 * 跟踪工作流管理器。
 *
 * <p>表达应用层对启动、停止某类跟踪监听任务的需求，具体线程和 MQTT 订阅实现放在适配层。</p>
 */
public interface TrackingWorkerManager {
    /**
     * 启动指定机组和跟踪类型的监听任务。
     */
    void start(String unitCode, TrackingType trackingType, String mqttTopic);

    /**
     * 停止指定机组和跟踪类型的监听任务。
     */
    void stop(String unitCode, TrackingType trackingType);
}
