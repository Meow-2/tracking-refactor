package com.wisdri.tracking.domain.repository.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;

import java.util.List;

/**
 * 异常数据存储端口。
 *
 * <p>用于持久化空数据、异常波动等检测结果，具体实现可落 PostgreSQL 或其他存储。</p>
 */
public interface AbnormalDataStorage {
    /**
     * 保存一批异常点位数据。
     */
    void save(List<AbnormalData> abnormalData);
}
