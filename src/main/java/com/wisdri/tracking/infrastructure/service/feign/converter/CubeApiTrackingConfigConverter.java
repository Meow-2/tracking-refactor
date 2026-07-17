package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;

/**
 * 单一跟踪类型的 Cube API 配置转换器。
 */
public interface CubeApiTrackingConfigConverter {
    /**
     * 是否支持指定跟踪类型。
     */
    boolean support(TrackingType trackingType);

    /**
     * 将一个跟踪类型目录转换为领域配置。
     */
    TrackingConfig convert(String unitCode, CubeApiTreeNode typeNode);
}
