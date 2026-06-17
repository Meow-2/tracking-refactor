package com.wisdri.tracking.application.tracking.impl;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.tracking.TrackingTaskApplicationService;
import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.port.storage.AbnormalDataStorage;
import com.wisdri.tracking.domain.port.storage.ProcessResultStorage;
import com.wisdri.tracking.domain.service.abnormal.AbnormalDataDetector;
import com.wisdri.tracking.domain.service.process.ProcessTrackingAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * 默认跟踪任务应用服务实现。
 */
@Slf4j
@Service
public class TrackingTaskApplicationServiceImpl implements TrackingTaskApplicationService {
    /**
     * 跟踪配置缓存。
     */
    @Resource
    private TrackingConfigCacheService configCache;

    /**
     * 异常数据检测服务。
     */
    @Resource
    private AbnormalDataDetector abnormalDataDetector;

    /**
     * 异常数据存储端口。
     */
    @Resource
    private AbnormalDataStorage abnormalDataStorage;

    /**
     * 过程跟踪算法。
     */
    @Resource
    private ProcessTrackingAlgorithm processTrackingAlgorithm;

    /**
     * 过程跟踪结果存储端口。
     */
    @Resource
    private ProcessResultStorage processResultStorage;

    /**
     * 处理跟踪任务，执行异常检测和对应跟踪算法。
     */
    @Override
    public void handle(TrackingTask task) {
        Optional<TrackingConfig> configOptional = configCache.get(task.getUnitCode(), task.getTrackingType());
        if (!configOptional.isPresent()) {
            log.warn("未找到缓存跟踪配置，unitCode={}, trackingType={}", task.getUnitCode(), task.getTrackingType());
            return;
        }
        TrackingConfig config = configOptional.get();
        List<AbnormalData> abnormalData = abnormalDataDetector.detect(task.getLatestSnapshot(), task.getPreviousSnapshot(), config);
        if (!abnormalData.isEmpty()) {
            abnormalDataStorage.save(abnormalData);
        }
        if (TrackingType.PROCESS == task.getTrackingType()) {
            List<ProcessResult> results = processTrackingAlgorithm.calculate(TrackingInput.of(task, config));
            if (!results.isEmpty()) {
                processResultStorage.save(results);
            }
        }
    }
}
