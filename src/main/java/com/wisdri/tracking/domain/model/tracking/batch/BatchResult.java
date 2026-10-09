package com.wisdri.tracking.domain.model.tracking.batch;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 单个模板实例、单个工艺侧的一条批次跟踪结果。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BatchResult extends TrackingResult {
    /**
     * 结果所属模板实例编码，例如 fb1；写时序库时映射为 fb_code。
     */
    private String templateCode;

    /**
     * 工艺侧编码，例如 north、south。
     */
    private String segmentCode;

    /**
     * 工艺侧显示名称。
     */
    private String segmentName;

    /**
     * 当前工艺侧绑定的钢卷号。
     */
    private String coilNo;

    /** 当前工艺侧该卷的重复生产序号；PG 取号暂时失败时为空。 */
    private Integer repeatProdNo;

    /**
     * 生成本条结果时读取到的生产状态值。
     */
    private BigDecimal productionStatus;

    /**
     * common 参数与当前工艺侧参数合并后的动态值。
     */
    private Map<String, Object> parameters;
}
