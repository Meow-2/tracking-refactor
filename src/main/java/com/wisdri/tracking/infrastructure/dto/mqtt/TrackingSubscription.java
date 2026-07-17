package com.wisdri.tracking.infrastructure.dto.mqtt;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 一个实际 MQTT topic 对应的跟踪上下文。
 */
@Data
@AllArgsConstructor
public class TrackingSubscription {
    /**
     * topic 所属的模板化跟踪配置。
     */
    private TrackingConfig config;

    /**
     * 模板实例编码；非模板跟踪类型为空。
     */
    private String templateCode;
}
