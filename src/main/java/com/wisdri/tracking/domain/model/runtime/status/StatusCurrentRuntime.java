package com.wisdri.tracking.domain.model.runtime.status;

import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 某一设备端当前识别状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusCurrentRuntime {
    /** 当前状态所属设备端。 */
    private DeviceSide side;
    /** 是否已识别出当前运行设备；false 表示业务字段已清空。 */
    private Boolean running;
    /** 当前状态的延迟清空计数，从 1 开始；候选命中时重置为 1。 */
    private Integer nullCount;
    /** 被识别设备的唯一编码。 */
    private String deviceCode;
    /** 被识别设备的展示名称。 */
    private String deviceName;
    /** 设备开卷或卷取方式代码。 */
    private String coilerMethod;
    /** 设备开卷或卷取方式名称。 */
    private String coilerMethodName;
    /** 当前设备上的钢卷号。 */
    private String coilNo;
    /** 钢卷重复生产次数；质量系统不可用时允许为空。 */
    private Integer productNo;
    /** 当前钢卷颜色号。 */
    private String colorNo;
    /** 当前帧识别到的剩余长度。 */
    private BigDecimal remainingLength;
    /** 当前钢卷采样窗口内的最大剩余长度。 */
    private BigDecimal maxLength;
}
