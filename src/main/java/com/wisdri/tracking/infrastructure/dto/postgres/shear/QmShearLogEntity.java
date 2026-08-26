package com.wisdri.tracking.infrastructure.dto.postgres.shear;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * PostgreSQL 剪切过程记录实体。
 */
@Data
@TableName("qm_shear_log")
public class QmShearLogEntity {
    /** qm_shear_log 主键，由 MyBatis-Plus 主键策略生成。 */
    @TableId
    private Long id;
    /** 产生剪切事件的机组代码。 */
    private String unitCode;
    /** 剪切归属的投入物料钢卷号。 */
    private String inMatNo;
    /** 剪切归属物料的生产次数。 */
    private String inMatNoProdNo;
    /** 配置提供的剪切类型整数编码，不保存 HEAD、SLICE、TAIL 枚举名。 */
    private Integer shearType;
    /** 实际关联设备代码与逻辑类型组合，例如 por1_head、tr2_tail。 */
    private String shearTypeName;
    /** 本刀剪切长度。 */
    private BigDecimal shearLength;
    /** 长度对应的设定刀数或飞剪设定片数；非点位计算长度为空。 */
    private Integer setNumber;
    /** 剪切触发帧的接收时间。 */
    private Instant shearTime;
    /** 事件发生时开卷机钢卷号。 */
    private String porCoilNo;
    /** 事件发生时开卷机颜色号。 */
    private String porColorCode;
    /** 事件发生时卷取机钢卷号。 */
    private String trCoilNo;
    /** 事件发生时卷取机颜色号。 */
    private String trColorCode;
    /** 事件发生时开卷机剩余长度。 */
    private BigDecimal porRemainLength;
    /** 事件发生时开卷机最大长度。 */
    private BigDecimal porMaxLength;
    /** 事件发生时卷取机剩余长度。 */
    private BigDecimal trRemainLength;
    /** 事件发生时卷取机最大长度。 */
    private BigDecimal trMaxLength;
    /** 当前完整剪切内刀次，从 1 开始。 */
    private Integer cutNo;
    /** 当前物料、剪刀和逻辑类型下的完整剪切序号，从 1 开始。 */
    private Integer shearNo;
}
