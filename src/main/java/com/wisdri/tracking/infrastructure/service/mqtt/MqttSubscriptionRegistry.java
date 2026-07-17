package com.wisdri.tracking.infrastructure.service.mqtt;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
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
 * 该类同时维护实际 topic 到 TrackingSubscription 的本地映射，并在 MQTT adapter 可用时
 * 将 topic 动态加入真实订阅。消息入口依赖本地映射反查模板配置和 templateCode。
 */
@Component
public class MqttSubscriptionRegistry {
    private static final String TEMPLATE_PLACEHOLDER = "{template}";

    /**
     * 实际 topic 到订阅上下文的映射。
     */
    private final ConcurrentMap<String, TrackingSubscription> subscriptions = new ConcurrentHashMap<>();

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
        if (config instanceof BatchTrackingConfig) {
            registerBatch((BatchTrackingConfig) config);
            return;
        }
        registerTopic(config.getMqttTopic(), config, null);
    }

    /**
     * 将一份批次模板配置展开为多个实际 topic。
     */
    private void registerBatch(BatchTrackingConfig config) {
        if (config.getTemplate() == null) {
            throw new IllegalArgumentException("注册批次订阅失败: template 不能为空");
        }
        if (!config.getMqttTopic().contains(TEMPLATE_PLACEHOLDER)) {
            throw new IllegalArgumentException("注册批次订阅失败: mqttTopic 必须包含 {template}");
        }
        for (String templateCode : config.getTemplate().resolveCodes()) {
            String topic = config.getMqttTopic().replace(TEMPLATE_PLACEHOLDER, templateCode);
            registerTopic(topic, config, templateCode);
        }
    }

    /**
     * 注册一个已经解析为实际字符串的 topic。
     */
    private void registerTopic(String topic, TrackingConfig config, String templateCode) {
        subscriptions.put(topic, new TrackingSubscription(config, templateCode));
        MqttPahoMessageDrivenChannelAdapter adapter = mqttAdapterProvider.getIfAvailable();
        if (adapter == null || !Boolean.TRUE.equals(config.getEnable())) {
            return;
        }
        adapter.addTopic(topic, mqttConfig.getQos());
    }

    /**
     * 根据 MQTT topic 查找对应订阅上下文。
     * <p>
     * topic 是 MQTT 消息入口能拿到的唯一业务定位信息，反查结果用于得到
     * unitCode、trackingType 和可选 templateCode。
     */
    public Optional<TrackingSubscription> find(String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(subscriptions.get(topic));
    }
}
