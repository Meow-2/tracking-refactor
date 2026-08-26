package com.wisdri.tracking.domain.service.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;

import java.util.List;

/**
 * 跟踪算法接口。
 *
 * <p>不同跟踪类型通过不同实现输出对应的跟踪结果子类。</p>
 */
public interface TrackingAlgorithm<R extends TrackingResult> {
    /**
     * 是否支持指定跟踪类型。
     */
    boolean support(TrackingType trackingType);

    /**
     * 执行跟踪计算。
     */
    List<R> calculate(TrackingInput input);

    /**
     * 结果持久化成功后的扩展动作，默认不处理。
     */
    default void afterPersist(TrackingInput input, List<R> results) {
        // 默认算法在 calculate 内维护运行态。
    }
}
