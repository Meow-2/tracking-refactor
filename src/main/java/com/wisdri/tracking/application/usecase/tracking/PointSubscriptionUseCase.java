package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 点位消息订阅应用用例。
 */
@Service
public class PointSubscriptionUseCase {
    /**
     * 当前实例跟踪配置。
     */
    @Resource
    private TrackingProperties trackingProperties;

    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher trackingRuntimeRepositoryDispatcher;

    /**
     * MQTT 订阅服务。
     */
    @Resource
    private MqttSubscriptionRegistry mqttSubscriptionRegistry;

    /**
     * 启动当前机组支持的所有点位消息订阅。
     * <p>
     * 启动时先刷新配置缓存，再按 TrackingType 枚举逐一查找当前机组配置。
     * 查到配置后交给 MQTT 订阅注册表注册 topic，后续消息入口再通过 topic
     * 反查到 unitCode 和 trackingType。
     */
    public void start() {
        trackingRuntimeRepositoryDispatcher.refreshConfig();
        for (TrackingType trackingType : TrackingType.values()) {
            Optional<TrackingConfig> configOptional = trackingRuntimeRepositoryDispatcher.findConfig(
                    trackingProperties.getUnit(),
                    trackingType
            );
            configOptional.ifPresent(config -> mqttSubscriptionRegistry.register(config));
        }
    }
}
