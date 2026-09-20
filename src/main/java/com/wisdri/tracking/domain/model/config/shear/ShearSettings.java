package com.wisdri.tracking.domain.model.config.shear;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一把剪刀的类型与长度设定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearSettings {
    /**
     * 可选固定判型；配置后跳过自动判型，但连续线出口固定切头或分切仍按颜色点选取物料。
     */
    @JsonProperty("default")
    private ShearKind defaultValue;
    /** 开卷机侧或非连续线卷取机侧切头的设定刀数点和单刀长度点。 */
    private CutSetting head;
    /** 开卷机侧或非连续线卷取机侧切尾的设定刀数点和单刀长度点。 */
    private CutSetting tail;
    /**
     * 飞剪切焊缝产生的总废料片数点；前侧取整数除以 2，余数归后侧。
     */
    private PointConfig welderPieces;
    /** 卷取机侧焊缝前方物料（上一卷切尾）的取样、废料和片长配置。 */
    private WelderShearSettings frontWelder;
    /** 卷取机侧焊缝后方物料（下一卷切头）的取样、废料和片长配置。 */
    private WelderShearSettings behindWelder;
}
