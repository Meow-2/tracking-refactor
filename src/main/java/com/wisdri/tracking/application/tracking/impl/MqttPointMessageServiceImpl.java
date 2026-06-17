package com.wisdri.tracking.application.tracking.impl;

import com.wisdri.tracking.application.config.TrackingConfigCacheService;
import com.wisdri.tracking.application.tracking.MqttPointMessageService;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointEvent;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.tracking.TrackingTaskPublisher;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.domain.service.point.PointEventDetector;
import com.wisdri.tracking.domain.service.point.PointExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认 MQTT 点位消息应用服务实现。
 */
@Slf4j
@Service
public class MqttPointMessageServiceImpl implements MqttPointMessageService {
    /**
     * 跟踪配置缓存。
     */
    @Resource
    private TrackingConfigCacheService configCache;

    /**
     * 上一条点位快照仓储。
     */
    @Resource
    private LastPointSnapshotRepository lastPointSnapshotRepository;

    /**
     * 点位提取服务。
     */
    @Resource
    private PointExtractor pointExtractor;

    /**
     * 跟踪事件检测服务。
     */
    @Resource
    private PointEventDetector eventDetector;

    /**
     * 跟踪任务发布端口。
     */
    @Resource
    private TrackingTaskPublisher taskPublisher;

    /**
     * 编排 MQTT 点位消息处理流程。
     */
    @Override
    public void handle(String unitCode, TrackingType trackingType, Map<String, Object> rawValues) {
        Optional<TrackingConfig> configOptional = configCache.get(unitCode, trackingType);
        if (!configOptional.isPresent()) {
            log.warn("未找到缓存跟踪配置，unitCode={}, trackingType={}", unitCode, trackingType);
            return;
        }
        TrackingConfig config = configOptional.get();
        if (!Boolean.TRUE.equals(config.getEnable())) {
            log.debug("跟踪功能未启用，unitCode={}, trackingType={}", unitCode, trackingType);
            return;
        }
        PointSnapshot latest = pointExtractor.extract(rawValues, config);
        PointSnapshot previous = lastPointSnapshotRepository.find(unitCode, trackingType).orElse(null);
        List<PointEvent> events = eventDetector.detect(latest, previous, config);
        if (!events.isEmpty()) {
            log.info("检测到跟踪事件，unitCode={}, trackingType={}, events={}", unitCode, trackingType, events);
        }
        taskPublisher.publish(TrackingTask.builder()
                .unitCode(unitCode)
                .trackingType(trackingType)
                .latestSnapshot(latest)
                .previousSnapshot(previous)
                .publishedAt(Instant.now())
                .build());
        lastPointSnapshotRepository.save(unitCode, trackingType, latest);
    }
}
