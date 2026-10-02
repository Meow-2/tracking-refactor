package com.wisdri.tracking.domain.model.config.ironloss;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import lombok.Data;

/** 铁损物料点位不参与推理，直接从当前 MQTT 快照读取。 */
@Data
public class IronLossTrackingSection {
    /** 固定点位的路径前缀，可为空；点位名称为完整路径时不再拼接。 */
    private String pointPrefix;

    /** 必填钢卷号点位，结果中保留原字符串。 */
    private PointConfig coilNo;

    /** 必填长度点位，结果写入 head_length；与启动点位独立配置。 */
    private PointConfig length;

    /** 可选启动条件；有配置时使用实际值大于等于 threshold。 */
    private StartCondition startCondition;
}
