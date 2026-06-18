package com.wisdri.tracking.domain.service.point.impl;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.service.point.PointReader;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 默认点位读取服务实现。
 */
@Component
public class PointReaderImpl implements PointReader {
    /**
     * 按字符串读取点位值。
     */
    @Override
    public String stringValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::stringValue).orElse(null);
    }

    /**
     * 按数字读取点位值。
     */
    @Override
    public BigDecimal decimalValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::decimalValue).orElse(null);
    }

    /**
     * 按布尔值读取点位值。
     */
    @Override
    public Boolean booleanValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::booleanValue).orElse(null);
    }

    /**
     * 读取原始点位值。
     */
    @Override
    public Object rawValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::getRawValue).orElse(null);
    }
}
