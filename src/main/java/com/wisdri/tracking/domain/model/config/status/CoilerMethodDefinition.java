package com.wisdri.tracking.domain.model.config.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个设备侧的开卷卷取方式定义，数组下标 0/1 分别对应 true/false。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoilerMethodDefinition {
    private List<String> name;
    private List<String> code;
}
