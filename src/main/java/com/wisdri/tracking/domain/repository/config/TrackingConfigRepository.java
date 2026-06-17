package com.wisdri.tracking.domain.repository.config;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 跟踪配置仓储端口。
 *
 * <p>领域层通过该接口读取和保存配置，具体配置可以来自 Redis、文件或数据库。</p>
 */
public interface TrackingConfigRepository {
    /**
     * 按机组代码和跟踪类型读取配置。
     */
    Optional<TrackingConfig> find(String unitCode, TrackingType trackingType);

    /**
     * 刷新并返回当前有效配置。
     */
    Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType);

    /**
     * 保存领域层格式的跟踪配置。
     */
    void save(TrackingConfig config);
}
