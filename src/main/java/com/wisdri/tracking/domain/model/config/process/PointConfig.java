package com.wisdri.tracking.domain.model.config.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点位配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointConfig {
    /**
     * 点位短名或完整路径。
     */
    private String name;

    /**
     * 点位数据类型。
     */
    private PointDataType type;
}
