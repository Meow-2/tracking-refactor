package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.infrastructure.properties.MqttConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MQTT 跟踪 topic 订阅注册表。
 * <p>
 * 该类同时维护 topic 到 TrackingConfig 的本地映射，并在 MQTT adapter 可用时
 * 将 topic 动态加入真实订阅。消息入口依赖本地映射完成 topic 反查。
 */
@Component
public class MqttSubscriptionRegistry {
    private final ConcurrentMap<String, TrackingConfig> configs = new ConcurrentHashMap<>();

    /**
     * MQTT 配置。
     */
    @Resource
    private MqttConfig mqttConfig;

    /**
     * MQTT adapter，可在未配置 mqtt.url 时不存在。
     */
    @Resource
    private ObjectProvider<MqttPahoMessageDrivenChannelAdapter> mqttAdapterProvider;

    /**
     * 注册单个跟踪配置。
     * <p>
     * 只要配置中存在 topic，就会写入本地映射，便于消息到达时反查配置；
     * 只有配置启用且 MQTT adapter 已创建时，才会真正向 broker 添加订阅。
     */
    public void register(TrackingConfig config) {
        if (config == null || config.getMqttTopic() == null || config.getMqttTopic().trim().isEmpty()) {
            return;
        }
        configs.put(config.getMqttTopic(), config);
        MqttPahoMessageDrivenChannelAdapter adapter = mqttAdapterProvider.getIfAvailable();
        if (adapter == null || !Boolean.TRUE.equals(config.getEnable())) {
            return;
        }
        adapter.addTopic(config.getMqttTopic(), mqttConfig.getQos());
    }

    /**
     * 根据 MQTT topic 查找对应跟踪配置。
     * <p>
     * topic 是 MQTT 消息入口能拿到的唯一业务定位信息，反查结果用于得到
     * unitCode 和 trackingType。
     */
    public Optional<TrackingConfig> find(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(configs.get(topic));
    }
}
