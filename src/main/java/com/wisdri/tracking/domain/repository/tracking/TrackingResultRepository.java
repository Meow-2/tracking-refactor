package com.wisdri.tracking.domain.repository.tracking;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.List;

/**
 * 单一跟踪结果类型仓储。
 */
public interface TrackingResultRepository<C extends TrackingConfig, T extends TrackingResult> {
    /**
     * 是否支持指定跟踪类型配置。
     */
    boolean support(TrackingType trackingType);

    /**
     * 创建或更新跟踪结果存储表。
     */
    void createTable(C config, PointSnapshot latestSnapshot);

    /**
     * 保存同一种类型的一批跟踪结果。
     */
    void save(List<T> results);
}
