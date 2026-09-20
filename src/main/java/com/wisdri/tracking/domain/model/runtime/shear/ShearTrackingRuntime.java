package com.wisdri.tracking.domain.model.runtime.shear;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单个设备的剪切算法状态。设备信息、物料组合、三类计数和幂等时间保存在同一 runtime。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShearTrackingRuntime extends TrackingRuntime {
    /** 设备唯一代码，同时作为 Redis 末级 key 和 runtime JSON 标识。 */
    private String deviceCode;
    /** 当前设备在 status 配置中的侧别。 */
    private DeviceSide side;
    /** 当前设备展示名称。 */
    private String deviceName;
    /** 当前设备卷号；空值表示当前帧未识别到钢卷。 */
    private String coilNo;
    /** 当前卷的生产次数；与卷号共同确定计数归属。 */
    private Integer productNo;
    /** 当前帧设备颜色号，已规范化便于匹配。 */
    private String colorNo;
    /** 当前设备剩余长度，与 status 点位使用相同单位。 */
    private BigDecimal remainingLength;
    /** 当前钢卷最大已观测长度，与剩余长度使用相同单位。 */
    private BigDecimal maxLength;
    /** 当前物料组合下的切头计数。 */
    @JsonProperty("HEAD")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShearCounterRuntime head;
    /** 当前物料组合下的分切计数。 */
    @JsonProperty("SLICE")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShearCounterRuntime slice;
    /** 当前物料组合下的切尾计数。 */
    @JsonProperty("TAIL")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShearCounterRuntime tail;
    /** 该触发设备最近一次成功写入剪切表的帧接收时间。 */
    private Instant lastPersistedTriggerTime;

    /** 读取指定剪切类型对应的计数状态。 */
    public ShearCounterRuntime counter(ShearKind kind) {
        if (kind == ShearKind.HEAD) {
            return head;
        }
        if (kind == ShearKind.TAIL) {
            return tail;
        }
        return slice;
    }

    /** 更新指定剪切类型对应的计数状态。 */
    public void setCounter(ShearKind kind, ShearCounterRuntime counter) {
        if (kind == ShearKind.HEAD) {
            head = counter;
        } else if (kind == ShearKind.TAIL) {
            tail = counter;
        } else {
            slice = counter;
        }
    }
}
