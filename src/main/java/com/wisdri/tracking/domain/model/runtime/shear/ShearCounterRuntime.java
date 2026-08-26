package com.wisdri.tracking.domain.model.runtime.shear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单个物料、单种剪切类型的刀次状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearCounterRuntime {
    /** 第几次完整剪切；剩余长度差为负或大于等于 shearExperience 时加一。 */
    private Integer shearNo;
    /** 当前完整剪切内的触发刀次；同一次完整剪切的有效触发沿依次加一。 */
    private Integer cutNo;
    /** 上一条成功持久化记录的开卷机剩余长度，用于计算下一刀的长度差。 */
    private BigDecimal lastPorRemainLength;
}
