package com.wisdri.tracking.domain.repository.runtime;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 跟踪配置和算法运行态仓储端口。
 */
public interface TrackingRuntimeRepository {
    /**
     * 是否支持指定跟踪类型。
     */
    boolean support(TrackingType trackingType);

    /**
     * 从缓存中读取当前配置对象引用。
     */
    Optional<TrackingConfig> findConfig(String unitCode, TrackingType trackingType);

    /**
     * 按指定配置类型读取当前配置对象引用。
     */
    default <T extends TrackingConfig> Optional<T> findConfigAs(String unitCode,
                                                                TrackingType trackingType,
                                                                Class<T> configType) {
        return findConfig(unitCode, trackingType)
                .filter(configType::isInstance)
                .map(configType::cast);
    }

    /**
     * 读取当前算法运行态，本地缓存未命中时从共享存储恢复。
     */
    Optional<TrackingRuntime> findRuntime(String unitCode, TrackingType trackingType);

    /**
     * 按指定运行态类型读取当前算法运行态。
     */
    default <T extends TrackingRuntime> Optional<T> findRuntimeAs(String unitCode,
                                                                  TrackingType trackingType,
                                                                  Class<T> runtimeType) {
        return findRuntime(unitCode, trackingType)
                .filter(runtimeType::isInstance)
                .map(runtimeType::cast);
    }

    /**
     * 保存当前算法运行态。
     */
    void saveRuntime(TrackingRuntime runtime);

    /**
     * 从外部配置源刷新全部配置。
     */
    void refreshConfig();
}
