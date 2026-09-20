package com.wisdri.tracking.domain.model.tracking.shear;

import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 一个剪切事件的完整判定结果；持有旧状态推演后的下一刀计数，供长度计算和提交共同使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearDecision {
    /** HEAD、SLICE 或 TAIL 逻辑类型。 */
    private ShearKind kind;
    /** 产生本次事件的设备快照，幂等时间归属该设备。 */
    private ShearDeviceSnapshot shearDevice;
    /** 剪切记录归属的设备快照，刀次计数归属该设备。 */
    private ShearDeviceSnapshot inMatDevice;
    /** 推演后的计数状态，将在单点判定完整成功后写入工作副本。 */
    private ShearCounterRuntime nextCounter;
    /** 本刀是否为该类型完整剪切组第一刀；分切首刀长度为零。 */
    private boolean firstCut;
    /** 更新前剩余长度与当前剩余长度的绝对差；首次计数时为空。 */
    private BigDecimal lengthDelta;
    /** 最终写入记录的剪切长度。 */
    private BigDecimal shearLength;
    /** 对应设定刀数或片数；分切时为空。 */
    private Integer setNumber;
}
