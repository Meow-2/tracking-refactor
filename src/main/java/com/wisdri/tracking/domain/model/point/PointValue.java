package com.wisdri.tracking.domain.model.point;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 统一封装点位值。
 *
 * <p>算法层通过该对象把原始值转换为字符串、数字或布尔值，避免在算法中散落类型判断。</p>
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PointValue {
    /**
     * 原始点位值，保留进入领域层时的数据形态。
     */
    private final Object rawValue;

    public static PointValue of(Object rawValue) {
        return new PointValue(rawValue);
    }

    /**
     * 将点位值按字符串读取，常用于钢卷号等文本点位。
     */
    public String stringValue() {
        return rawValue == null ? null : String.valueOf(rawValue);
    }

    /**
     * 将点位值按高精度数字读取，常用于长度、速度、温度等数值点位。
     */
    public BigDecimal decimalValue() {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof BigDecimal) {
            return (BigDecimal) rawValue;
        }
        if (rawValue instanceof Number) {
            return new BigDecimal(String.valueOf(rawValue));
        }
        String text = stringValue();
        return text == null || text.trim().isEmpty() ? null : new BigDecimal(text.trim());
    }

    /**
     * 将点位值按布尔值读取，兼容 true/false 与 1/0 两类现场数据格式。
     */
    public Boolean booleanValue() {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Boolean) {
            return (Boolean) rawValue;
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).intValue() != 0;
        }
        String text = stringValue();
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
}
