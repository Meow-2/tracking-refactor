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
     * 设定刀数点；切头或切尾的每一刀（含首刀）都与 {@link #length} 一起读取并写入 set_number。
     */
    private PointConfig number;
    /**
     * 单刀剪切长度点；切头或切尾的每一刀（含首刀）都读取该点。
     */
    private PointConfig length;
}
