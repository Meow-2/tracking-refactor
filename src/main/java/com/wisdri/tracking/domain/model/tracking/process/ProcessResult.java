package com.wisdri.tracking.domain.model.tracking.process;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 单条过程跟踪结果记录。
 *
 * <p>一次算法运行会按 segments 生成多条该对象，每条对象对应一个工艺段。</p>
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ProcessResult extends TrackingResult {
    /**
     * 工艺段编码。
     */
    private String segmentCode;

    /**
     * 工艺段名称。
     */
    private String segmentName;

    /**
     * 当前跟踪结果对应的钢卷号。
     */
    private String coilNo;

    /**
     * 带头长度值。
     */
    private BigDecimal headLength;

    /**
     * 速度值。
     */
    private BigDecimal speed;

    /**
     * 道次号，仅轧机模式下有值。
     */
    private Integer passNo;

    /**
     * 动态工艺参数，key 为参数点位短名，value 为参数值。
     */
    private Map<String, Object> parameters;
}
