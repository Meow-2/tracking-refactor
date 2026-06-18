package com.wisdri.tracking.domain.repository.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;

import java.util.List;

/**
 * 跟踪结果仓储端口。
 *
 * <p>领域层通过该接口保存不同跟踪类型的算法输出。</p>
 */
public interface TrackingResultRepository {
    /**
     * 保存一批跟踪结果。
     */
    void save(List<? extends TrackingResult> results);
}
