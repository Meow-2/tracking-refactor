package com.wisdri.tracking.application.config;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 跟踪配置刷新应用服务。
 *
 * <p>负责在钢卷变化等事件发生时编排远程配置获取、配置转换和配置保存。</p>
 */
public interface TrackingConfigRefreshService {
    /**
     * 刷新指定机组和跟踪类型的配置。
     */
    Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType);
}
