package com.wisdri.tracking.application.config.impl;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存跟踪配置缓存实现。
 *
 * <p>同一应用实例内按 unitCode + trackingType 保存当前有效配置。</p>
 */
@Component
public class TrackingConfigCacheServiceImpl implements TrackingConfigCacheService {
    /**
     * 当前实例持有的配置缓存。
     */
    private final Map<CacheKey, TrackingConfig> configs = new ConcurrentHashMap<>();

    /**
     * 按机组代码和跟踪类型读取缓存配置。
     */
    @Override
    public Optional<TrackingConfig> get(String unitCode, TrackingType trackingType) {
        return Optional.ofNullable(configs.get(new CacheKey(unitCode, trackingType)));
    }

    /**
     * 写入或覆盖缓存配置。
     */
    @Override
    public void put(TrackingConfig config) {
        configs.put(new CacheKey(config.getUnitCode(), config.getTrackingType()), config);
    }

    /**
     * 删除指定机组和跟踪类型的缓存配置。
     */
    @Override
    public void evict(String unitCode, TrackingType trackingType) {
        configs.remove(new CacheKey(unitCode, trackingType));
    }

    /**
     * 清空全部缓存配置。
     */
    @Override
    public void evictAll() {
        configs.clear();
    }

    /**
     * 配置缓存 key。
     */
    private static class CacheKey {
        /**
         * 机组代码。
         */
        private final String unitCode;

        /**
         * 跟踪类型。
         */
        private final TrackingType trackingType;

        /**
         * 创建缓存 key。
         */
        private CacheKey(String unitCode, TrackingType trackingType) {
            this.unitCode = unitCode;
            this.trackingType = trackingType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey)) {
                return false;
            }
            CacheKey cacheKey = (CacheKey) other;
            return java.util.Objects.equals(unitCode, cacheKey.unitCode) && trackingType == cacheKey.trackingType;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(unitCode, trackingType);
        }
    }
}
