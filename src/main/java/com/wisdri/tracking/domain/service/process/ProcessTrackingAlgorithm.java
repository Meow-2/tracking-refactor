package com.wisdri.tracking.domain.service.process;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;

import java.util.List;

/**
 * 过程跟踪算法接口。
 *
 * <p>输入跟踪配置与点位快照，输出每个工艺段对应的跟踪结果。</p>
 */
public interface ProcessTrackingAlgorithm {
    /**
     * 执行过程跟踪计算。
     */
    List<ProcessResult> calculate(TrackingInput input);
}
