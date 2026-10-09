package com.wisdri.tracking.domain.model.tracking.coiler;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 待写入 qm_dc_coiler_log 的开卷卷取结果。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CoilerResult extends TrackingResult {
    /** 从 status 结果直接传递的加工单元代码。 */
    private String cellCode;
    private String inMatNo;
    private Integer repeatProdNo;
    /**
     * 轧机道次号；非轧机 status 结果为空。
     */
    private Integer passNo;
    private String coilerMethod;
    private String coilerMethodName;
    private String coilerDeviceCode;
    private String coilerDeviceName;
    private BigDecimal coilerMaxLength;
    private String uncoilerMethod;
    private String uncoilerMethodName;
    private String uncoilerDeviceCode;
    private String uncoilerDeviceName;
    private BigDecimal uncoilerMaxLength;
}
