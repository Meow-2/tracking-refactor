package com.wisdri.tracking.domain.model.config.process;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 过程跟踪配置。
 *
 * <p>只承载过程跟踪算法需要的配置项。</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessTrackingConfig extends TrackingConfig {
    /**
     * 过程跟踪算法主配置。
     */
    private TrackingSection tracking;

    /**
     * 工艺段配置列表，每个元素会生成一条过程跟踪结果。
     */
    private List<SegmentConfig> segments;
}
