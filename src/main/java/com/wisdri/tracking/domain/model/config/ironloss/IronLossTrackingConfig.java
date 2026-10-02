package com.wisdri.tracking.domain.model.config.ironloss;

import com.wisdri.tracking.domain.model.config.TrackingConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** 单机组铁损配置：固定物料点位在 tracking，参数点位在 tech 工艺段。 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IronLossTrackingConfig extends TrackingConfig {
    /** 钢卷号、长度及可选启动条件。 */
    private IronLossTrackingSection tracking;

    /** 工艺段按 Cube 顺序处理，每段生成一条结果并对应一张表。 */
    private List<IronLossSegmentConfig> segments;
}
