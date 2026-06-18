package com.wisdri.tracking.domain.repository.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.List;

/**
 * 异常数据存储端口。
 *
 * <p>用于持久化空数据、异常波动等检测结果，具体实现可落 PostgreSQL 或其他存储。</p>
 */
public interface AbnormalDataRepository {

    List<AbnormalData> find(String unitCode, TrackingType trackingType);

    /**
     * 保存一批异常点位数据。
     */
    void save(List<AbnormalData> abnormalData);
}
