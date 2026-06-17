package com.wisdri.tracking.application.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Map;

/**
 * MQTT 点位消息应用服务。
 *
 * <p>负责处理 MQTT 接收侧传入的原始点位数据，并编排配置读取、点位提取、任务发布和快照更新。</p>
 */
public interface MqttPointMessageApplicationService {
    /**
     * 处理一条 MQTT 点位消息。
     *
     * @param unitCode 当前机组代码
     * @param trackingType 跟踪类型
     * @param rawValues 原始点位值集合
     */
    void handle(String unitCode, TrackingType trackingType, Map<String, Object> rawValues);
}
