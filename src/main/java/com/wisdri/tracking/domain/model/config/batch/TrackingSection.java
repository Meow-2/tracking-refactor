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

    /**
     * 各工艺侧的卷号点位配置；JSON 字段使用单数 point。
     */
    @JsonProperty("point")
    private List<TrackingPointGroup> points;
}
