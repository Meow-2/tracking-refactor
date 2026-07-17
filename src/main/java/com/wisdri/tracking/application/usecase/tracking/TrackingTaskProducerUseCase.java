package com.wisdri.tracking.application.usecase.tracking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 跟踪任务生产用例。
 * <p>
 * 该用例承接已经归一化后的 topic 和 payload，负责反查跟踪配置、解析点位快照，
 * 构造跟踪任务并进入后续 RocketMQ 链路。
 */
@Slf4j
@Service
public class TrackingTaskProducerUseCase {
    private static final TypeReference<Map<String, Object>> POINT_VALUE_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper objectMapper = JsonUtils.decimalPreservingMapper();

    /**
     * MQTT 订阅注册表。
     */
    @Resource
    private MqttSubscriptionRegistry mqttSubscriptionRegistry;

    /**
     * 跟踪配置仓储。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    /**
     * 最新点位快照仓储。
     */
    @Resource
    private LastPointSnapshotRepository lastPointSnapshotRepository;

    /**
     * 跟踪任务生产者。
     */
    @Resource
    private TrackingTaskProducer trackingTaskProducer;

    /**
     * 处理指定 topic 的点位消息 payload。
     * <p>
     * topic 未注册时直接忽略，避免未知来源消息进入跟踪链路；注册成功后，
     * payload 必须是 JSON object，并会被解析为 PointSnapshot。
     */
    public void handle(String topic, String payload) {
        Optional<TrackingSubscription> subscriptionOptional = mqttSubscriptionRegistry.find(topic);
        if (!subscriptionOptional.isPresent()) {
            log.warn("收到未注册的 MQTT 跟踪消息，topic={}", topic);
            return;
        }
        TrackingSubscription subscription = subscriptionOptional.get();
        TrackingConfig config = subscription.getConfig();
        PointSnapshot latestSnapshot = parse(payload);
        produce(config.getUnitCode(), config.getTrackingType(), subscription.getTemplateCode(), latestSnapshot);
    }

    /**
     * 根据最新点位快照构造跟踪任务并发送到 RocketMQ。
     * <p>
     * 发布前会重新读取配置，确保禁用的跟踪类型不会继续产生任务。
     * 任务中同时携带上一帧快照和当前快照，供后续跟踪算法计算增量状态；
     * 发送成功后再保存当前快照，作为下一条消息的 previousSnapshot。
     */
    private void produce(String unitCode,
                         TrackingType trackingType,
                         String templateCode,
                         PointSnapshot latestSnapshot) {
        Optional<TrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfig(unitCode, trackingType);
        if (!configOptional.isPresent() || !Boolean.TRUE.equals(configOptional.get().getEnable())) {
            return;
        }
        PointSnapshot previousSnapshot = lastPointSnapshotRepository
                .find(unitCode, trackingType, templateCode)
                .orElse(null);
        TrackingTask task = TrackingTask.builder()
                .unitCode(unitCode)
                .trackingType(trackingType)
                .templateCode(templateCode)
                .latestSnapshot(latestSnapshot)
                .previousSnapshot(previousSnapshot)
                .publishedAt(Instant.now())
                .build();
        lastPointSnapshotRepository.save(unitCode, trackingType, templateCode, latestSnapshot);
        trackingTaskProducer.send(task);
    }

    /**
     * 将 JSON object payload 解析为点位快照。
     * <p>
     * 浮点数按 BigDecimal 反序列化，避免过程数据在进入算法前发生精度损失。
     */
    private PointSnapshot parse(String payload) {
        try {
            Map<String, Object> values = objectMapper.readValue(payload, POINT_VALUE_TYPE);
            return PointSnapshot.builder()
                    .values(values)
                    .receivedAt(Instant.now())
                    .build();
        } catch (IOException e) {
            throw new TrackingException("解析 MQTT 点位消息失败", e);
        }
    }
}
