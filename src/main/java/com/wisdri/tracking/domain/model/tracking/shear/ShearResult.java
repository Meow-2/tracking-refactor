package com.wisdri.tracking.domain.model.tracking.shear;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisdri.tracking.domain.model.runtime.shear.ShearRuntimeCommit;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 3.0 剪切过程记录及其内部 runtime 提交信息。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShearResult extends TrackingResult {
    /** 剪切事件所属加工单元代码；道次为空时使用默认序号 1，越界时为空。 */
    private String cellCode;
    /** 剪切信号点名，仅供诊断，不落入 qm_dc_shear_log。 */
    private String shearPointCode;
    /** 触发剪切的设备代码，幂等时间归属此设备。 */
    private String deviceCode;
    /** HEAD、SLICE、TAIL 逻辑类型。 */
    private ShearKind shearKind;
    /** 物料设备对应的钢卷号。 */
    private String inMatNo;
    /** 物料设备对应钢卷的生产次数。 */
    private String repeatProdNo;
    /** 当前剪刀配置的剪切类型编码。 */
    private String shearType;
    /** 触发设备代码与逻辑类型组成的名称。 */
    private String shearTypeName;
    /** 本刀剪切长度，与配置点和设备剩余长度使用相同单位。 */
    private BigDecimal shearLength;
    /** 对应设定刀数或片数；分切时为空。 */
    private Integer setNumber;
    /** 当前完整剪切组内的刀次。 */
    private Integer cutNo;
    /** 当前物料类型对应的完整剪切组号。 */
    private Integer shearNo;
    /** 剪切归属物料设备代码。 */
    private String inMatDeviceCode;
    /** 剪切归属物料设备颜色号。 */
    private String inMatDeviceColorNo;
    /** 剪切归属物料设备剩余长度。 */
    private BigDecimal inMatDeviceRemainLength;
    /** 剪切归属物料设备最大长度。 */
    private BigDecimal inMatDeviceMaxLength;
    /** 触发剪切设备上的卷号，可为空。 */
    private String shearDeviceCoilNo;
    /** 触发剪切设备颜色号。 */
    private String shearDeviceColorNo;
    /** 触发剪切设备剩余长度。 */
    private BigDecimal shearDeviceRemainLength;
    /** 触发剪切设备最大长度。 */
    private BigDecimal shearDeviceMaxLength;
    /** 剪切帧时间，亦作为设备级幂等标记候选值。 */
    private Instant shearTime;
    /** 不序列化、不入库；保存 calculate 暂存的 runtime，以便写库成功后原样提交。 */
    @JsonIgnore
    private ShearRuntimeCommit runtimeCommit;
}
