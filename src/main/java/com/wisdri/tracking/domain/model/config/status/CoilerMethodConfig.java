package com.wisdri.tracking.domain.model.config.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisdri.tracking.domain.model.config.PointDataType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单台设备的方式选择配置；name/type 存在时读取点位，否则使用 default。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoilerMethodConfig {
    private String name;
    private PointDataType type;
    @JsonProperty("default")
    private Boolean defaultValue;
    /**
     * 点位值为 false 时对应 coiler_method_def 的数组下标。
     */
    private Integer falseIndex;
}
