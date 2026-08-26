package com.wisdri.tracking.domain.model.runtime.shear;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单把剪刀推理出的开卷机或卷取机当前状态及其适用剪切计数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearDeviceRuntime {
    private DeviceSide side;
    private Boolean running;
    private String deviceCode;
    private String deviceName;
    private String coilNo;
    private Integer productNo;
    private String colorNo;
    private BigDecimal remainingLength;
    private BigDecimal maxLength;

    @JsonProperty("HEAD")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShearCounterRuntime head;

    @JsonProperty("SLICE")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShearCounterRuntime slice;

    @JsonProperty("TAIL")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ShearCounterRuntime tail;

    public ShearCounterRuntime counter(ShearKind kind) {
        if (kind == ShearKind.HEAD) {
            return head;
        }
        if (kind == ShearKind.TAIL) {
            return tail;
        }
        return slice;
    }

    public void setCounter(ShearKind kind, ShearCounterRuntime counter) {
        if (kind == ShearKind.HEAD) {
            head = counter;
        } else if (kind == ShearKind.TAIL) {
            tail = counter;
        } else {
            slice = counter;
        }
    }
}
