package com.wisdri.tracking.domain.model.tracking.ironloss;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/** 一帧铁损数据在一个工艺段产生的列式存储结果。 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IronLossResult extends TrackingResult {
    /** 工艺段编码，用于定位时序表。 */
    private String segmentCode;
    /** 固定钢卷号，保持消息中的字符串值。 */
    private String coilNo;
    /** STATUS 匹配的重复生产次数；匹配不到时为空。 */
    private Integer inMatNoProdNo;
    /** 固定点位直接读取的带头长度。 */
    private BigDecimal headLength;
    /** 机组代码加三位加工单元序号；序号无效时为空。 */
    private String cellCode;
    /** 本段已上报的原始工艺参数，键为 Cube 点位短名。 */
    private Map<String, Object> parameters;
}
