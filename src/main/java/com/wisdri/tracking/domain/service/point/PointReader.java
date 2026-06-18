package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.point.PointSnapshot;

import java.math.BigDecimal;

/**
 * 点位读取服务。
 *
 * <p>负责从点位快照中按完整点位路径安全读取点位值。</p>
 */
public interface PointReader {
    /**
     * 按字符串读取点位值。
     */
    String stringValue(PointSnapshot snapshot, String pointPath);

    /**
     * 按数字读取点位值。
     */
    BigDecimal decimalValue(PointSnapshot snapshot, String pointPath);

    /**
     * 按布尔值读取点位值。
     */
    Boolean booleanValue(PointSnapshot snapshot, String pointPath);

    /**
     * 读取原始点位值。
     */
    Object rawValue(PointSnapshot snapshot, String pointPath);
}
