package com.wisdri.tracking.domain.model.config.shear;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 入口剪某种剪切类型的设定点位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutSetting {
    /**
     * 设定刀数点；当剪切长度读取 {@link #length} 点位时，其值同步写入 set_number。
     */
    private PointConfig number;
    /**
     * 单刀剪切长度点；某类型首刀按算法固定为 0 时不读取该点。
     */
    private PointConfig length;
}
