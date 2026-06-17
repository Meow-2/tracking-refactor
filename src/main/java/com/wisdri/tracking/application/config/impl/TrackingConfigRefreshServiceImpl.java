package com.wisdri.tracking.application.config.impl;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.config.TrackingConfigRefreshService;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.config.TrackingConfigRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Optional;

/**
 * 默认配置刷新应用服务。
 *
 * <p>第三方远程配置接口接入前，该实现只保留应用层入口。</p>
 */
@Service
public class TrackingConfigRefreshServiceImpl implements TrackingConfigRefreshService {
    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingConfigRepository configRepository;

    /**
     * 跟踪配置缓存。
     */
    @Resource
    private TrackingConfigCacheService configCache;

    /**
     * 刷新指定机组、跟踪类型的配置。
     */
    @Override
    public Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType) {
        Optional<TrackingConfig> configOptional = configRepository.find(unitCode, trackingType);
        if (configOptional.isPresent()) {
            configCache.put(configOptional.get());
        } else {
            configCache.evict(unitCode, trackingType);
        }
        return configOptional;
    }
}
