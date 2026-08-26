package com.wisdri.tracking.domain.model.config.shear;

import com.wisdri.tracking.domain.model.config.PointDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 非连续线光栅点位配置，当前连续线版本仅保留配置结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GratingPointConfig {
    /** 光栅占位信号点名。 */
    private String name;
    /** 光栅信号数据类型。 */
    private PointDataType type;
    /** 点位表示“有钢卷占位”时对应的布尔值。 */
    private Boolean hasCoil;
}
