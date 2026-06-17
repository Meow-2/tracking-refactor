package com.wisdri.tracking.application.config;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 跟踪配置缓存。
 *
 * <p>应用运行期统一从该缓存读取配置，Redis 只作为启动加载和刷新时的配置来源。</p>
 */
public interface TrackingConfigCacheService {
    /**
     * 按机组代码和跟踪类型读取缓存配置。
     */
    Optional<TrackingConfig> get(String unitCode, TrackingType trackingType);

    /**
     * 写入或覆盖缓存配置。
     */
    void put(TrackingConfig config);

    /**
     * 删除指定机组和跟踪类型的缓存配置。
     */
    void evict(String unitCode, TrackingType trackingType);

    /**
     * 清空全部缓存配置。
     */
    void evictAll();
}
