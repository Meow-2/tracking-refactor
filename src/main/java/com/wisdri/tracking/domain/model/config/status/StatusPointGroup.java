package com.wisdri.tracking.domain.model.config.status;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单台开卷机或卷取机的卷号、可选色号、剩余长度点位组合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusPointGroup {
    /** 设备编码；位置模式下 por 前缀表示独立开卷机，tr 前缀表示可换向卷取机。 */
    private String code;
    /** 设备展示名称，随状态结果和运行态保存。 */
    private String name;
    /** 设备物理位置 right/left；为空时沿用原有的 side 换向规则。 */
    private DevicePosition position;
    /** 未启用位置模式时的初始侧别；位置模式中标识 por/tr 的设备类别。 */
    private DeviceSide side;
    /** 当前设备的钢卷号点位，按 tracking.pointPrefix 解析。 */
    private PointConfig coilNo;
    /** 当前设备的可选色号点位，未配置时不读取。 */
    private PointConfig colorNo;
    /** 当前设备的剩余长度点位，用于趋势识别及带头长度计算。 */
    private PointConfig remainingLength;
    /** 开卷卷取方式选择配置，依据设备在本道次的实际侧别取方式定义。 */
    private CoilerMethodConfig coilerMethod;
}
