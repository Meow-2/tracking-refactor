package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 状态跟踪结果暂不持久化；保留仓储实现以完成统一结果分发流程。
 */
@Repository
public class StatusTrackingResultRepositoryImpl
        implements TrackingResultRepository<StatusTrackingConfig, StatusResult> {
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.STATUS == trackingType;
    }

    @Override
    public void createTable(StatusTrackingConfig config) {
        // 状态结果不创建时序表。
    }

    @Override
    public void save(List<StatusResult> results) {
        // 状态结果不写入时序数据库。
    }
}
