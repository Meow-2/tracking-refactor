package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import com.wisdri.tracking.domain.model.config.PointDataType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TimeSeriesDataType {

    BOOLEAN("boolean", "布尔型"),
    CHAR("char", "字符型"),
    BYTE("byte", "字节型"),
    SHORT("short", "短整型"),
    INT("int", "整型"),
    LONG("long", "长整型"),
    FLOAT("float", "单精度浮点型"),
    DOUBLE("double", "双精度浮点型"),
    STRING("string", "字符串型"),
    DATE("date", "时间戳型");

    private final String code;
    private final String desc;

    /**
     * 将跟踪点位类型一一映射为 quality-ts 创建表接口支持的类型。
     */
    public static TimeSeriesDataType fromPointDataType(PointDataType pointDataType) {
        if (pointDataType == null) {
            throw new NullPointerException("点位数据类型不能为空");
        }
        return valueOf(pointDataType.name());
    }
}
