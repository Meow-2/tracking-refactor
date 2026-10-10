package com.wisdri.tracking.domain.model.history;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 指定机组和跟踪类型的历史处理元信息，聚合算法配置和配置节点下的点位历史查询映射。
 * <p>
 * 每次从 Cube 独立加载，点位按完整路径索引。
 */
@Getter
public class TrackingHistoryMetadata {
    /** 对应跟踪类型的算法配置，实际类型由 trackingType 决定。 */
    private final TrackingConfig trackingConfig;
    /** 配置节点子树内的点位查询元数据，按完整路径索引且不可增删。 */
    private final Map<String, PointHistoryMetadata> pointMetadata;

    public TrackingHistoryMetadata(TrackingConfig trackingConfig, Map<String, PointHistoryMetadata> pointMetadata) {
        this.trackingConfig = trackingConfig;
        this.pointMetadata = Collections.unmodifiableMap(new LinkedHashMap<>(pointMetadata));
    }
}
