package com.wisdri.tracking.domain.model.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * 跟踪点位数据类型。
 */
@Getter
@RequiredArgsConstructor
public enum PointDataType {
    FLOAT("float", "浮点型"),
    STRING("string", "字符串型"),
    INT("int", "整型"),
    BOOL("bool", "布尔型");

    private final String code;
    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 忽略大小写解析 JSON 中的点位类型编码。
     */
    @JsonCreator
    public static PointDataType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PointDataType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return PointDataType.valueOf(code.toUpperCase(Locale.ROOT));
    }
}
