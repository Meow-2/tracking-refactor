package com.wisdri.tracking.domain.model.config.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批次跟踪参数目录配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentConfig {
    /**
     * MQTT 快照中的点位路径前缀，可包含 {template} 占位符。
     */
    private String pointPrefix;

    /**
     * 稳定的目录编码，同时用于定位 Cube 中对应类型的目录。
     */
    private String code;

    /**
     * 目录显示名称。
     */
    private String name;

    /**
     * Cube 物理点位编码中需要移除的前缀，可包含 {template} 占位符。
     */
    private String cubeParsingPrefix;

    /**
     * 当前目录的参数点位；JSON 字段使用单数 point。
     */
    @JsonProperty("point")
    private List<PointConfig> points;
}
