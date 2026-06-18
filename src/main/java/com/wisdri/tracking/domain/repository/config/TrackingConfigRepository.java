package com.wisdri.tracking.domain.repository.config;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 跟踪配置仓储端口。
 *
 * <p>领域层通过该接口读取当前缓存配置，或触发外层刷新缓存。
 * 实现类应返回缓存中的配置对象引用，不应返回临时反序列化对象或防御性拷贝。
 * 刷新配置时应更新缓存对象本身，保证已持有该配置引用的领域流程可以继续使用最新配置。</p>
 */
public interface TrackingConfigRepository {
    /**
     * 从缓存中按机组代码和跟踪类型读取当前配置对象引用。
     */
    Optional<TrackingConfig> find(String unitCode, TrackingType trackingType);

    /**
     * 从缓存中按指定配置类型读取当前配置对象引用。
     */
    default <T extends TrackingConfig> Optional<T> findAs(String unitCode, TrackingType trackingType, Class<T> configType) {
        return find(unitCode, trackingType)
                .filter(configType::isInstance)
                .map(configType::cast);
    }

    /**
     * 刷新缓存对象本身并返回刷新后的当前配置对象引用。
     */
    Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType);

    /**
     * 按指定配置类型刷新缓存对象本身并返回刷新后的当前配置对象引用。
     */
    default <T extends TrackingConfig> Optional<T> refreshAs(String unitCode, TrackingType trackingType, Class<T> configType) {
        return refresh(unitCode, trackingType)
                .filter(configType::isInstance)
                .map(configType::cast);
    }
}
