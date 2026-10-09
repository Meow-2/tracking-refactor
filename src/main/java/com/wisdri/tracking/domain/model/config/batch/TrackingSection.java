package com.wisdri.tracking.domain.model.config.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisdri.tracking.domain.model.config.StartCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批次跟踪主配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingSection {
    /**
     * 跟踪点位路径前缀，可包含 {template}。
     */
    private String pointPrefix;

    /**
     * 跟踪启动条件。
     */
    private StartCondition startCondition;

    /** 卷号缺值清空阈值；有效帧计数为 1，默认值 1 表示首个空帧即清空。 */
    @Builder.Default
    private Integer currentClearThreshold = 1;

    /**
     * 各工艺侧的卷号点位配置；JSON 字段使用单数 point。
     */
    @JsonProperty("point")
    private List<TrackingPointGroup> points;
}
