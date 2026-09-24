package com.wisdri.tracking.domain.repository.runtime;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;
import java.util.List;

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
     * 读取当前算法运行态；具体是否从共享存储恢复由仓储实现和跟踪类型决定。
     */
    Optional<TrackingRuntime> findRuntime(String unitCode, TrackingType trackingType);

    /**
     * 读取指定模板实例的算法运行态。
     * <p>
     * 默认实现保持非模板仓储兼容；需要实例隔离的仓储应覆盖此方法。
     */
    default Optional<TrackingRuntime> findRuntime(String unitCode,
                                                   TrackingType trackingType,
                                                   String templateCode) {
        return findRuntime(unitCode, trackingType);
    }

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
     * 按指定运行态类型读取模板实例运行态。
     */
    default <T extends TrackingRuntime> Optional<T> findRuntimeAs(String unitCode,
                                                                  TrackingType trackingType,
                                                                  String templateCode,
                                                                  Class<T> runtimeType) {
        return findRuntime(unitCode, trackingType, templateCode)
                .filter(runtimeType::isInstance)
                .map(runtimeType::cast);
    }

    /**
     * 保存当前算法运行态。
     */
    void saveRuntime(TrackingRuntime runtime);

    /**
     * 批量保存同一帧产生的 runtime；实现必须先校验并序列化完整集合，再更新本地状态。
     * 运行态仓储需要在此入口内统一完成 Redis 写入，避免逐条保存留下半帧状态。
     */
    void saveRuntimes(List<? extends TrackingRuntime> runtimes);

    /**
     * 从外部配置源刷新全部配置。
     */
    void refreshConfig();
}
