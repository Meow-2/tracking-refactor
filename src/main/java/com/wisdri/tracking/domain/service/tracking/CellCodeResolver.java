package com.wisdri.tracking.domain.service.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.service.point.PointReader;

import java.math.BigDecimal;
import java.util.Locale;

/** PROCESS 与 IRONLOSS 共用的加工单元代码生成规则。 */
public final class CellCodeResolver {
    private CellCodeResolver() {
    }

    /**
     * 固定序号和点位二选一；点位配置后无效值不回退到默认值。
     * 返回大写机组代码和三位序号，范围外或非整数序号返回 null。
     */
    public static String resolve(String unitCode, PointSnapshot snapshot, String pointPrefix,
                                 Integer fixedValue, PointConfig point) {
        Object raw = point == null ? (fixedValue == null ? 1 : fixedValue)
                : PointReader.rawValue(snapshot, PointReader.pathResolve(pointPrefix, point.getName()));
        Integer number = number(raw);
        if (number == null || unitCode == null || unitCode.trim().isEmpty()) {
            return null;
        }
        return unitCode.toUpperCase(Locale.ROOT) + String.format(Locale.ROOT, "%03d", number);
    }

    private static Integer number(Object raw) {
        if (raw == null || raw instanceof Boolean) {
            return null;
        }
        try {
            int value = new BigDecimal(String.valueOf(raw).trim()).intValueExact();
            return value >= 0 && value <= 999 ? value : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }
}
