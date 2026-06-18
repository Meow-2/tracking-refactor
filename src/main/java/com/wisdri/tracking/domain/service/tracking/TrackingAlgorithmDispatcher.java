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
    public List<? extends TrackingResult> calculate(TrackingInput input) {
        if (input == null || algorithms == null) {
            return new ArrayList<>();
        }
        TrackingType trackingType = input.getTrackingType();
        for (TrackingAlgorithm<? extends TrackingResult> algorithm : algorithms) {
            if (algorithm.support(trackingType)) {
                return algorithm.calculate(input);
            }
        }
        return new ArrayList<>();
    }
}
