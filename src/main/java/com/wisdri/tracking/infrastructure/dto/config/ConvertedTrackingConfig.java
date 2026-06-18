package com.wisdri.tracking.infrastructure.dto.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 从外部配置树转换后的单个跟踪配置。
 */
@Getter
@AllArgsConstructor
public class ConvertedTrackingConfig {
    /**
     * 机组代码。
     */
    private final String unitCode;

    /**
     * 跟踪类型。
     */
    private final TrackingType trackingType;

    /**
     * 可写入 Redis 的配置内容。
     */
    private final JsonNode config;
}
