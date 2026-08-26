package com.wisdri.tracking.domain.model.runtime.shear;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisdri.tracking.domain.model.runtime.TrackingRuntime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 单个 por_tr_code 对应的一把剪刀运行态。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShearTrackingRuntime extends TrackingRuntime {
    /**
     * 当前运行态对应的开卷机或卷取机代码，同时作为 Redis 目录下的末级 key。
     */
    @JsonIgnore
    private String porTrCode;

    /** 算法推理出的当前开卷机状态。 */
    private ShearDeviceRuntime uncoiler;

    /** 算法推理出的当前卷取机状态。 */
    private ShearDeviceRuntime coiler;
}
