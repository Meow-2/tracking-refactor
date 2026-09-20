package com.wisdri.tracking.domain.model.runtime.shear;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单帧暂存的剪切 runtime。计算期间只改副本，数据库成功后才提交剪切结果对应状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShearRuntimeCommit {
    /** 当前帧各设备最终状态，键为设备代码；计数归属物料设备。 */
    @Builder.Default
    private Map<String, ShearTrackingRuntime> runtimes = new LinkedHashMap<>();
    /** 已生成剪切记录的触发设备及其帧接收时间；成功入库后更新该设备幂等标记。 */
    @Builder.Default
    private Map<String, Instant> persistedTriggerTimes = new LinkedHashMap<>();
}
