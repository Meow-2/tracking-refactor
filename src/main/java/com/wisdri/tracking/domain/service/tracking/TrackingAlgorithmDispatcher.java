package com.wisdri.tracking.domain.service.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 跟踪算法分发器。
 */
@Component
public class TrackingAlgorithmDispatcher {
    /**
     * 跟踪算法列表。
     */
    @Resource
    private List<TrackingAlgorithm<? extends TrackingResult>> algorithms;

    /**
     * 按跟踪类型选择算法并执行计算。
     */
    public List<TrackingResult> calculate(TrackingInput input) {
        if (input == null || algorithms == null) {
            return new ArrayList<>();
        }
        TrackingType trackingType = input.getTrackingType();
        for (TrackingAlgorithm<? extends TrackingResult> algorithm : algorithms) {
            if (algorithm.support(trackingType)) {
                List<? extends TrackingResult> results = algorithm.calculate(input);
                return results == null ? new ArrayList<>() : new ArrayList<>(results);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 将结果持久化成功事件回调给对应算法。
     */
    @SuppressWarnings("unchecked")
    public void afterPersist(TrackingInput input, List<? extends TrackingResult> results) {
        if (input == null || algorithms == null || results == null || results.isEmpty()) {
            return;
        }
        for (TrackingAlgorithm<? extends TrackingResult> algorithm : algorithms) {
            if (algorithm.support(input.getTrackingType())) {
                ((TrackingAlgorithm<TrackingResult>) algorithm).afterPersist(
                        input, new ArrayList<>(results));
                return;
            }
        }
    }
}
