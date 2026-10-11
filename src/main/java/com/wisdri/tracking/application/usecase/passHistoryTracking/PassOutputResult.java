package com.wisdri.tracking.application.usecase.passHistoryTracking;

import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/** 单道次历史补全结果，记录质量数据、写入状态与跳过或失败原因。 */
@Data
public class PassOutputResult {
    private Integer passNo;
    private LocalDateTime switchTime;
    /** READY 表示待写入；公开接口返回 SUCCESS、SKIPPED 或 FAILED。 */
    private String status;
    private String reason;
    private RollingPassOutput output;
    /** 首末帧所用点位的原始时间，键为“帧时间/点位路径”。 */
    private Map<String, LocalDateTime> sourceTimes;
}
