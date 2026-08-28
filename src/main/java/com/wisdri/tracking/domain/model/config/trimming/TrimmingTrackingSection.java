package com.wisdri.tracking.domain.model.config.trimming;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.process.TrackingPointGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 切边跟踪主配置，字段结构与过程跟踪保持兼容。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrimmingTrackingSection {
    private String pointPrefix;
    private PointConfig speedPoint;
    private StartCondition startCondition;
    private TrimmingLengthMode lengthMode;
    private List<TrackingPointGroup> points;
}
