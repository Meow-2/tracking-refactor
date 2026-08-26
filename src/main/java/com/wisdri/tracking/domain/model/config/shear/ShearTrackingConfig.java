package com.wisdri.tracking.domain.model.config.shear;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 剪切跟踪配置。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShearTrackingConfig extends TrackingConfig {
    /**
     * 剪切算法主体配置，对应 JSON 中的 {@code tracking} 对象。
     */
    private ShearTrackingSection tracking;
}
