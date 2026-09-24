package com.wisdri.tracking.domain.model.config.status;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * 轧机两侧设备的物理位置。
 * <p>位置不会随轧制方向变化；设备在本道次承担开卷或卷取职责由算法决定。</p>
 */
public enum DevicePosition {
    /** 轧机右侧，对应实际方向为 false 时的物料来源侧。 */
    RIGHT("right"),
    /** 轧机左侧，对应实际方向为 true 时的物料来源侧。 */
    LEFT("left");

    /** Cube 配置中的位置编码，按小写形式写入 JSON。 */
    private final String code;

    DevicePosition(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    /** 将 Cube 配置的 right/left 转为设备位置，非法值由配置转换器报错。 */
    @JsonCreator
    public static DevicePosition fromCode(String code) {
        return code == null ? null : valueOf(code.toUpperCase(Locale.ROOT));
    }
}
