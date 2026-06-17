package com.wisdri.tracking.domain.repository.point;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.Optional;

/**
 * 上一条点位快照仓储端口。
 *
 * <p>领域层只关心上一条快照的读取和保存，不关心底层是否使用 Redis lastdata key。</p>
 */
public interface LastPointSnapshotRepository {
    /**
     * 查询指定机组和跟踪类型的上一条点位快照。
     */
    Optional<PointSnapshot> find(String unitCode, TrackingType trackingType);

    /**
     * 保存指定机组和跟踪类型的最新点位快照，供下一次消息处理使用。
     */
    void save(String unitCode, TrackingType trackingType, PointSnapshot snapshot);
}
