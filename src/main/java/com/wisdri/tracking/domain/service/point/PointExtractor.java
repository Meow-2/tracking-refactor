package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;

import java.util.Map;

/**
 * 点位提取服务。
 *
 * <p>负责按照跟踪配置从原始点位表中提取算法需要的点位快照。</p>
 */
public interface PointExtractor {
    /**
     * 将原始点位数据转换为领域层点位快照。
     */
    PointSnapshot extract(Map<String, Object> rawValues, TrackingConfig config);
}
