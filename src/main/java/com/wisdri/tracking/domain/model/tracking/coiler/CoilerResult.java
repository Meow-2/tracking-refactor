package com.wisdri.tracking.domain.model.tracking.coiler;

import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * 待写入 qm_coiler_log 的开卷卷取结果。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CoilerResult extends TrackingResult {
    private String inMatNo;
    private Integer inMatNoProdNo;
    private String coilerMethod;
    private String coilerMethodName;
    private String deviceCode;
    private String deviceName;
    private BigDecimal maxLength;
}
