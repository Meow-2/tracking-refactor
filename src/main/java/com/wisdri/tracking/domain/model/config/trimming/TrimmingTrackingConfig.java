package com.wisdri.tracking.domain.model.config.trimming;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 切边跟踪配置。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TrimmingTrackingConfig extends TrackingConfig {
    private TrimmingTrackingSection tracking;
    private List<SegmentConfig> segments;
}
