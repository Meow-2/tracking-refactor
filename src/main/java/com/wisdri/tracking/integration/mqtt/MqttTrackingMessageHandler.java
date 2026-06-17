package com.wisdri.tracking.integration.mqtt;

import com.wisdri.tracking.application.tracking.MqttPointMessageApplicationService;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * MQTT 跟踪消息处理适配器。
 *
 * <p>负责把 MQTT payload 转换为应用层入参，并调用应用服务。</p>
 */
@Component
public class MqttTrackingMessageHandler {
    /**
     * MQTT 原始消息映射器。
     */
    @Resource
    private MqttRawMessageMapper mapper;

    /**
     * MQTT 点位消息应用服务。
     */
    @Resource
    private MqttPointMessageApplicationService applicationService;

    /**
     * 处理指定机组和跟踪类型的一条 MQTT 消息。
     */
    public void handle(String unitCode, TrackingType trackingType, String payload) {
        Map<String, Object> rawValues = mapper.toRawValues(payload);
        applicationService.handle(unitCode, trackingType, rawValues);
    }
}
