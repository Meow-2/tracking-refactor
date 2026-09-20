package com.wisdri.tracking.domain.model.tracking.shear;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 从 status candidates 提取的单设备只读快照，不包含 shear runtime 的可变计数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearDeviceSnapshot {
    /** 当前设备侧别，来自 status 设备配置；不从延迟保留的 current 结果推断。 */
    private DeviceSide side;
    /** status candidates 中设备的唯一代码。 */
    private String deviceCode;
    /** status 配置或当前候选快照中的设备名称。 */
    private String deviceName;
    /** 当前设备上的卷号；非连续线出口剪允许触发设备卷号为空。 */
    private String coilNo;
    /** 当前卷生产次数。 */
    private Integer productNo;
    /** 去不可见字符并数值规范化后的颜色号。 */
    private String colorNo;
    /** 当前候选窗口的最新剩余长度，与 status 长度单位一致。 */
    private BigDecimal remainingLength;
    /** status 候选窗口观测到的最大长度。 */
    private BigDecimal maxLength;
    /** 候选卷号和长度是否有效；空卷出口剪仍可作为剪切设备。 */
    private boolean dataComplete;

    /** 判断该快照是否能作为剪切结果归属物料。 */
    public boolean hasCompleteMaterial() {
        return dataComplete && coilNo != null && !coilNo.trim().isEmpty()
                && productNo != null && remainingLength != null;
    }
}
