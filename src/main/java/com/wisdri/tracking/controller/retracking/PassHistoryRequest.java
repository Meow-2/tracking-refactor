package com.wisdri.tracking.controller.retracking;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/** 道次历史补全时间范围，使用上海时间，可带小数秒。 */
@Data
public class PassHistoryRequest {
    @NotBlank(message = "startTime 不能为空")
    private String startTime;
    @NotBlank(message = "endTime 不能为空")
    private String endTime;
}
