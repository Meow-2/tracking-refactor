package com.wisdri.tracking.domain.repository.config;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 跟踪配置仓储端口。
 *
 * <p>领域层通过该接口读取当前缓存配置，或触发外层刷新缓存。</p>
 */
public interface TrackingConfigRepository {
    /**
     * 从缓存中按机组代码和跟踪类型读取当前配置。
     */
    Optional<TrackingConfig> find(String unitCode, TrackingType trackingType);

    /**
     * 从缓存中按指定配置类型读取当前配置。
     */
    default <T extends TrackingConfig> Optional<T> findAs(String unitCode, TrackingType trackingType, Class<T> configType) {
        return find(unitCode, trackingType)
                .filter(configType::isInstance)
                .map(configType::cast);
    }

    /**
     * 刷新缓存并返回刷新后的当前配置。
     */
    Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType);

    /**
     * 按指定配置类型刷新缓存并返回刷新后的当前配置。
     */
    default <T extends TrackingConfig> Optional<T> refreshAs(String unitCode, TrackingType trackingType, Class<T> configType) {
        return refresh(unitCode, trackingType)
                .filter(configType::isInstance)
                .map(configType::cast);
    }
}
