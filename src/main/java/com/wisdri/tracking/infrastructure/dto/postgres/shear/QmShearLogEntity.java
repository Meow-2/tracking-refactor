package com.wisdri.tracking.infrastructure.dto.postgres.shear;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** qm_dc_shear_log 剪切过程记录实体，与剪切算法领域结果一一对应。 */
@Data
@TableName("qm_dc_shear_log")
public class QmShearLogEntity {
    /** 数据库主键，由 MyBatis-Plus assign_id 策略生成。 */
    @TableId
    private Long id;
    /** 产生剪切事件的机组代码。 */
    private String unitCode;
    /** 剪切归属物料设备上的钢卷号。 */
    private String inMatNo;
    /** 剪切归属物料钢卷的生产次数。 */
    @TableField("in_mat_prod_no")
    private String inMatNoProdNo;
    /** 配置提供的剪切类型编码。 */
    private String shearType;
    /** 触发设备代码与 head/slice/tail 的组合名称。 */
    private String shearTypeName;
    /** 本刀剪切长度，与机组长度点位使用相同单位。 */
    private BigDecimal shearLength;
    /** 对应设定刀数或片数；分切时为空。 */
    private Integer setNumber;
    /** 触发帧接收时间。 */
    private Instant shearTime;
    /** 触发剪切事件的设备代码，与 shear_time 一起标识剪刀侧记录来源。 */
    @TableField("shear_device_code")
    private String shearDeviceCode;
    /** 物料归属设备代码。 */
    private String inMatDeviceCode;
    /** 物料归属设备颜色号。 */
    private String inMatDeviceColorNo;
    /** 物料归属设备剩余长度。 */
    private BigDecimal inMatDeviceRemainLength;
    /** 物料归属设备最大长度。 */
    private BigDecimal inMatDeviceMaxLength;
    /** 触发设备当前卷号，可为空。 */
    private String shearDeviceCoilNo;
    /** 触发设备当前颜色号。 */
    private String shearDeviceColorNo;
    /** 触发设备剩余长度。 */
    private BigDecimal shearDeviceRemainLength;
    /** 触发设备最大长度。 */
    private BigDecimal shearDeviceMaxLength;
    /** 当前完整剪切组内刀次。 */
    private Integer cutNo;
    /** 当前物料类型对应的完整剪切组号。 */
    private Integer shearNo;
}
