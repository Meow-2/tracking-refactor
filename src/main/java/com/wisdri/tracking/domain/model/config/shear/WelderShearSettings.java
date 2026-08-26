package com.wisdri.tracking.domain.model.config.shear;

import com.wisdri.tracking.domain.model.config.PointConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 连续线出口剪焊缝前后设定点位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WelderShearSettings {
    /** 该焊缝方向的取样片数点；与废料片数之和写入 set_number。 */
    private PointConfig samplePieces;
    /** 该焊缝方向的废料片数点；与取样片数之和写入 set_number。 */
    private PointConfig scrapPieces;
    /**
     * 取样片与废料片共用的片长点；配置后优先于 sampleLength、scrapLength。
     */
    private PointConfig length;
    /** 取样片单片长度点；仅在未配置 {@link #length} 时使用。 */
    private PointConfig sampleLength;
    /** 废料片单片长度点；仅在未配置 {@link #length} 时使用。 */
    private PointConfig scrapLength;
}
