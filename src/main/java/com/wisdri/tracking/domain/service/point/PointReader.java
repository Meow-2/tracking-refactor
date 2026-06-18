package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 点位读取工具。
 *
 * <p>负责从点位快照中按完整点位路径安全读取点位值。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PointReader {
    /**
     * 按字符串读取点位值。
     */
    public static String stringValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::stringValue).orElse(null);
    }

    /**
     * 按数字读取点位值。
     */
    public static BigDecimal decimalValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::decimalValue).orElse(null);
    }

    /**
     * 按布尔值读取点位值。
     */
    public static Boolean booleanValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::booleanValue).orElse(null);
    }

    /**
     * 读取原始点位值。
     */
    public static Object rawValue(PointSnapshot snapshot, String pointPath) {
        return snapshot.value(pointPath).map(PointValue::getRawValue).orElse(null);
    }

    /**
     * 根据点位前缀和点位名称构造完整点位路径。
     */
    public static String pathResolve(String prefix, String point) {
        if (point == null || point.isEmpty()) {
            return point;
        }
        if (point.startsWith("/") || prefix == null || prefix.isEmpty() || point.startsWith(prefix)) {
            return point;
        }
        return prefix + point;
    }
}
