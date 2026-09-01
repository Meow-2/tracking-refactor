package com.wisdri.tracking.domain.model.config.shear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一把剪刀各逻辑类型对应的数据库编码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearTypeCodes {
    /** 切头判型写入 qm_dc_shear_log.shear_type 的字符串编码。 */
    private String head;
    /** 分切判型写入 qm_dc_shear_log.shear_type 的字符串编码。 */
    private String slice;
    /** 切尾判型写入 qm_dc_shear_log.shear_type 的字符串编码。 */
    private String tail;
}
