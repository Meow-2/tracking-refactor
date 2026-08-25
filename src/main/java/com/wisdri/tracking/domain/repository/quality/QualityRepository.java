package com.wisdri.tracking.domain.repository.quality;

/**
 * 质量服务查询契约。
 */
public interface QualityRepository {
    /**
     * 根据钢卷号查询重复生产次数。
     */
    Integer queryProductNo(String coilNo);
}
