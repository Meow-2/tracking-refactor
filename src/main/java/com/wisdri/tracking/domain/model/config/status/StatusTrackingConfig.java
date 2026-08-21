package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 开卷机、卷取机当前钢卷状态跟踪配置。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StatusTrackingConfig extends TrackingConfig {
    private StatusTrackingSection tracking;
}
