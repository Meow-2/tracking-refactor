package com.wisdri.tracking.domain.model.tracking.shear;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单次剪切记录及其 runtime 提交信息。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShearResult extends TrackingResult {
    /** 剪切信号点名，也是提交 runtime 时定位剪刀的键；不写入 qm_shear_log。 */
    private String shearPointCode;
    /** 实际关联的开卷机或卷取机代码，用于定位独立的 shear runtime。 */
    private String porTrCode;
    /** 算法判定的逻辑类型 HEAD、SLICE 或 TAIL，不等同于数据库整数编码。 */
    private ShearKind shearKind;
    /** 本次剪切归属的投入物料钢卷号。 */
    private String inMatNo;
    /** 本次剪切归属物料的生产次数。 */
    private String inMatNoProdNo;
    /** 当前剪刀 typeCodes 提供、最终写入 qm_shear_log.shear_type 的整数编码。 */
    private Integer shearType;
    /**
     * 实际关联的开卷机或卷取机代码与逻辑类型组合，例如 por1_head、tr2_tail。
     */
    private String shearTypeName;
    /** 本刀剪切长度；单位与配置长度点和 status 剩余长度保持一致。 */
    private BigDecimal shearLength;
    /**
     * 长度对应的设定数量：入口取 number 点，飞剪取 samplePieces + scrapPieces + 本侧焊缝片数；
     * 固定 0 或剩余长度差等非点位长度为 null。
     */
    private Integer setNumber;
    /** 剪切触发帧的接收时间。 */
    private Instant shearTime;
    /** 判型时对应开卷机的钢卷号。 */
    private String porCoilNo;
    /** 判型时对应开卷机的原始颜色号。 */
    private String porColorCode;
    /** 判型时对应卷取机的钢卷号。 */
    private String trCoilNo;
    /** 判型时对应卷取机的原始颜色号。 */
    private String trColorCode;
    /** 判型和刀次计算时使用的开卷机剩余长度。 */
    private BigDecimal porRemainLength;
    /** status 上下文中的开卷机最大长度。 */
    private BigDecimal porMaxLength;
    /** status 上下文中的卷取机剩余长度。 */
    private BigDecimal trRemainLength;
    /** status 上下文中的卷取机最大长度。 */
    private BigDecimal trMaxLength;
    /** 当前完整剪切内的刀次，从 1 开始。 */
    private Integer cutNo;
    /** 当前物料、剪刀及逻辑类型下的完整剪切序号，从 1 开始。 */
    private Integer shearNo;
}
