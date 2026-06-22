package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 跟踪任务消费用例。
 * <p>
 * 该用例接收 RocketMQ 中的 TrackingTask，调用领域算法计算跟踪结果，
 * 再通过结果仓储分发器保存。
 */
@Service
public class TrackingTaskConsumerUseCase {
    /**
     * 跟踪算法分发器。
     */
    @Resource
    private TrackingAlgorithmDispatcher trackingAlgorithmDispatcher;

    /**
     * 跟踪结果仓储分发器。
     */
    @Resource
    private TrackingResultRepositoryDispatcher trackingResultRepositoryDispatcher;

    /**
     * 消费单个跟踪任务。
     */
    public void consume(TrackingTask task) {
        if (task == null) {
            return;
        }
        List<? extends TrackingResult> results = trackingAlgorithmDispatcher.calculate(TrackingInput.of(task));
        if (results == null || results.isEmpty()) {
            return;
        }
        save(results);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void save(List<? extends TrackingResult> results) {
        trackingResultRepositoryDispatcher.save((List) results);
    }
}
