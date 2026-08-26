package com.wisdri.tracking.domain.model.runtime.shear;

import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一把剪刀上单个物料的各类型刀次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearMaterialRuntime {
    /** 物料钢卷号，用于诊断 runtime 键及提交结果。 */
    private String coilNo;
    /** 同一钢卷号的生产次数，参与隔离返工或重复生产的数据。 */
    private String productNo;
    /** 当前物料在 HEAD、SLICE、TAIL 三种逻辑类型下相互独立的刀次计数。 */
    @Builder.Default
    private Map<ShearKind, ShearCounterRuntime> counters = new LinkedHashMap<>();
}
