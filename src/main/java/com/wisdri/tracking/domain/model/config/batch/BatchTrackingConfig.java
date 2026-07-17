package com.wisdri.tracking.domain.model.config.batch;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 单个机组的批次跟踪配置。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchTrackingConfig extends TrackingConfig {
    /**
     * 模板定义，用于生成 fb1、fb2 等具体模板实例编码。
     */
    private TemplateConfig template;

    /**
     * 批次状态判断及各工艺侧卷号点位配置。
     */
    private TrackingSection tracking;

    /**
     * 批次参数目录列表；每个元素对应 Cube 中一种目录。
     */
    private List<SegmentConfig> segments;
}
