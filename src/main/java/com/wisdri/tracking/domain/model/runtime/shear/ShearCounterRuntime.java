package com.wisdri.tracking.domain.model.runtime.shear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单个物料组合、单种剪切类型的刀次状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearCounterRuntime {
    /** 完整剪切组号，从 1 开始；达到长度阈值后递增。 */
    private Integer shearNo;
    /** 当前完整剪切组内刀次，从 1 开始。 */
    private Integer cutNo;
    /** 上一次成功提交的该类剪切后，物料设备的剩余长度。 */
    private BigDecimal lastRemainingLength;
}
