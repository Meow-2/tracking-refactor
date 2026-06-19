package com.wisdri.tracking.domain.service.point;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
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
        Object value = rawValue(snapshot, pointPath);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 按数字读取点位值。
     */
    public static BigDecimal decimalValue(PointSnapshot snapshot, String pointPath) {
        Object value = rawValue(snapshot, pointPath);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(String.valueOf(value));
        }
        String text = stringValue(snapshot, pointPath);
        return text == null || text.trim().isEmpty() ? null : new BigDecimal(text.trim());
    }

    /**
     * 按布尔值读取点位值。
     */
    public static Boolean booleanValue(PointSnapshot snapshot, String pointPath) {
        Object value = rawValue(snapshot, pointPath);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = stringValue(snapshot, pointPath);
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if ("1".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("0".equals(normalized)) {
            return Boolean.FALSE;
        }
        return Boolean.valueOf(normalized);
    }

    /**
     * 读取原始点位值。
     */
    public static Object rawValue(PointSnapshot snapshot, String pointPath) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.value(pointPath).orElse(null);
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
