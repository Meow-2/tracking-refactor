package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 跟踪任务消费用例。
 * <p>
 * 该用例接收 RocketMQ 中的 TrackingInput，调用领域算法计算跟踪结果，
 * 再通过结果仓储分发器保存。
 */
@Service
@Slf4j
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
    public void consume(TrackingInput input) {
        if (input == null) {
            return;
        }
        if (TrackingType.STATUS == input.getTrackingType()) {
            log.info("忽略消费侧 status 跟踪任务，status 已迁移到生产侧同步计算，机组编码={}",
                    input.getUnitCode());
            return;
        }
        List<TrackingResult> results = trackingAlgorithmDispatcher.calculate(input);
        if (results == null || results.isEmpty()) {
            return;
        }
        trackingResultRepositoryDispatcher.save(results);
        trackingAlgorithmDispatcher.afterPersist(input, results);
        log.info("跟踪任务处理完成，机组编码={}，跟踪类型={}，模板编码={}，结果数量={}",
                input.getUnitCode(), input.getTrackingType(), input.getTemplateCode(), results.size());
    }
}
