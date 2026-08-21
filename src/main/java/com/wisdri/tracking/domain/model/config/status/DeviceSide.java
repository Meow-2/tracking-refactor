package com.wisdri.tracking.domain.model.config.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * 钢卷状态跟踪设备端类型。
 */
@Getter
@RequiredArgsConstructor
public enum DeviceSide {
    UNCOILER("uncoiler", "开卷机"),
    COILER("coiler", "卷取机");

    private final String code;
    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static DeviceSide fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeviceSide side : values()) {
            if (side.code.equalsIgnoreCase(code)) {
                return side;
            }
        }
        return DeviceSide.valueOf(code.toUpperCase(Locale.ROOT));
    }
}
