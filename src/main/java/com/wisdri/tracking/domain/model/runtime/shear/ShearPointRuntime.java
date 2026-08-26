package com.wisdri.tracking.domain.model.runtime.shear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单把剪刀的物料刀次集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearPointRuntime {
    /**
     * 当前剪刀处理过的物料运行态；键由“卷号 + 生产次数”组合得到。
     */
    @Builder.Default
    private Map<String, ShearMaterialRuntime> materials = new LinkedHashMap<>();
}
