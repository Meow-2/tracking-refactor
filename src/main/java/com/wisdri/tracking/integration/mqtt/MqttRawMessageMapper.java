package com.wisdri.tracking.integration.mqtt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MQTT 原始消息映射器。
 *
 * <p>负责把 MQTT payload JSON 转换为原始点位值集合。</p>
 */
@Component
public class MqttRawMessageMapper {
    /**
     * MQTT payload JSON 对象类型。
     */
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Object>>() {
            };

    /**
     * JSON 序列化工具。
     */
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 将 MQTT payload 转换为原始点位 Map。
     */
    public Map<String, Object> toRawValues(String payload) {
        try {
            return objectMapper.readValue(payload, MAP_TYPE);
        } catch (IOException e) {
            throw new IllegalArgumentException("MQTT 点位消息 JSON 解析失败", e);
        }
    }
}
