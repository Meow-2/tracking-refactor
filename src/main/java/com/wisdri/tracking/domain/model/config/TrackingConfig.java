package com.wisdri.tracking.domain.model.config;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 单个机组、单个跟踪类型的完整业务配置。
 *
 * <p>该对象是领域层使用的配置模型，不直接绑定 Redis JSON 字段或存储结构。</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class TrackingConfig {
    /**
     * 机组代码，例如 CP1、ZRM1。
     */
    private String unitCode;

    /**
     * 跟踪类型，例如过程跟踪、剪切跟踪。
     */
    private TrackingType trackingType;

    /**
     * 当前跟踪功能是否启用。
     */
    private Boolean enable;

    /**
     * 当前跟踪功能订阅的 MQTT 主题。
     */
    private String mqttTopic;
}
