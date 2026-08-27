package com.wisdri.tracking.domain.model.config.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开卷机侧和卷取机侧的方式定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoilerMethodDefinitions {
    private CoilerMethodDefinition uncoiler;
    private CoilerMethodDefinition coiler;

    public CoilerMethodDefinition definition(DeviceSide side) {
        return side == DeviceSide.UNCOILER ? uncoiler : coiler;
    }
}
