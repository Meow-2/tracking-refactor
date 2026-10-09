package com.wisdri.tracking.application.usecase.tracking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.common.utils.JsonUtils;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCoilCacheEntry;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.repository.point.LastPointSnapshotRepository;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import com.wisdri.tracking.infrastructure.service.mqtt.MqttSubscriptionRegistry;
import com.wisdri.tracking.infrastructure.dto.mqtt.TrackingSubscription;
import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * 跟踪算法分发器；status 在任务生产侧同步执行。
     */
    @Resource
    private TrackingAlgorithmDispatcher trackingAlgorithmDispatcher;

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
        TrackingInput input = TrackingInput.builder()
                .unitCode(unitCode)
                .trackingType(trackingType)
                .templateCode(templateCode)
                .latestSnapshot(latestSnapshot)
                .previousSnapshot(previousSnapshot)
                .statusContext(TrackingType.STATUS == trackingType ? null : statusContext(unitCode))
                .build();
        lastPointSnapshotRepository.save(unitCode, trackingType, templateCode, latestSnapshot);
        if (TrackingType.STATUS == trackingType) {
            publishCoilerTask(unitCode,
                    trackingAlgorithmDispatcher.calculate(input));
            return;
        }
        trackingTaskProducer.send(input);
    }

    /**
     * 状态算法检测到钢卷号变化时，发布一条批量开卷卷取跟踪任务。
     */
    private void publishCoilerTask(String unitCode, List<TrackingResult> calculated) {
        List<StatusResult> results = new ArrayList<>();
        if (calculated != null) {
            for (TrackingResult result : calculated) {
                if (result instanceof StatusResult) {
                    results.add(copyStatusResult((StatusResult) result));
                }
            }
        }
        if (results.isEmpty()) {
            return;
        }
        trackingTaskProducer.send(TrackingInput.builder()
                .unitCode(unitCode)
                .trackingType(TrackingType.COILER)
                .statusContext(StatusTrackingContext.builder()
                        .results(results)
                        .build())
                .build());
    }

    /**
     * 深拷贝当前状态运行态并固化到任务，避免消费时读取到另一帧状态。
     */
    private StatusTrackingContext statusContext(String unitCode) {
        Optional<StatusTrackingRuntime> runtimeOptional = runtimeRepositoryDispatcher.findRuntimeAs(
                unitCode, TrackingType.STATUS, StatusTrackingRuntime.class);
        if (!runtimeOptional.isPresent()) {
            return null;
        }
        StatusTrackingRuntime runtime = runtimeOptional.get();
        return StatusTrackingContext.builder()
                .receivedAt(runtime.getReceivedAt())
                .startConditionPointValue(runtime.getStartConditionPointValue())
                .rollingDirection(runtime.getRollingDirection())
                .rollingDirectReverse(runtime.getRollingDirectReverse())
                .passNo(runtime.getPassNo())
                .candidates(copyCandidates(runtime.getCandidates()))
                .current(copyCurrent(runtime.getCurrent()))
                .coilCache(copyCoilCache(runtime.getCoilCache()))
                .build();
    }

    /** 缓存条目也需复制，避免后续 status 更新颜色号影响已发送任务。 */
    private Map<String, StatusCoilCacheEntry> copyCoilCache(Map<String, StatusCoilCacheEntry> source) {
        Map<String, StatusCoilCacheEntry> copied = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((coilNo, entry) -> {
                if (entry != null) {
                    copied.put(coilNo, StatusCoilCacheEntry.builder()
                            .repeatProdNo(entry.getRepeatProdNo())
                            .colorNo(entry.getColorNo())
                            .build());
                }
            });
        }
        return copied;
    }

    private Map<String, StatusCandidateRuntime> copyCandidates(
            Map<String, StatusCandidateRuntime> source) {
        Map<String, StatusCandidateRuntime> copied = new LinkedHashMap<>();
        if (source == null) {
            return copied;
        }
        source.forEach((code, candidate) -> {
            if (candidate != null) {
                List<BigDecimal> lengths = candidate.getLengths() == null
                        ? new ArrayList<>() : new ArrayList<>(candidate.getLengths());
                copied.put(code, StatusCandidateRuntime.builder()
                        .deviceCode(candidate.getDeviceCode() == null ? code : candidate.getDeviceCode())
                        .deviceName(candidate.getDeviceName())
                        .dataComplete(candidate.getDataComplete())
                        .nullCount(candidate.getNullCount())
                        .coilNo(candidate.getCoilNo())
                        .repeatProdNo(candidate.getRepeatProdNo())
                        .colorNo(candidate.getColorNo())
                        .coilerMethod(candidate.getCoilerMethod())
                        .coilerMethodName(candidate.getCoilerMethodName())
                        .maxLength(candidate.getMaxLength())
                        .lengths(lengths)
                        .build());
            }
        });
        return copied;
    }

    private Map<DeviceSide, StatusCurrentRuntime> copyCurrent(
            Map<DeviceSide, StatusCurrentRuntime> source) {
        Map<DeviceSide, StatusCurrentRuntime> copied = new LinkedHashMap<>();
        if (source == null) {
            return copied;
        }
        source.forEach((side, current) -> {
            if (current != null) {
                copied.put(side, StatusCurrentRuntime.builder()
                        .side(current.getSide())
                        .running(current.getRunning())
                        .nullCount(current.getNullCount())
                        .deviceCode(current.getDeviceCode())
                        .deviceName(current.getDeviceName())
                        .coilerMethod(current.getCoilerMethod())
                        .coilerMethodName(current.getCoilerMethodName())
                        .coilNo(current.getCoilNo())
                        .repeatProdNo(current.getRepeatProdNo())
                        .colorNo(current.getColorNo())
                        .remainingLength(current.getRemainingLength())
                        .maxLength(current.getMaxLength())
                        .build());
            }
        });
        return copied;
    }

    private StatusResult copyStatusResult(StatusResult source) {
        return StatusResult.builder()
                .unitCode(source.getUnitCode())
                .trackingType(source.getTrackingType())
                .generatedAt(source.getGeneratedAt())
                .receivedAt(source.getReceivedAt())
                .side(source.getSide())
                .running(source.getRunning())
                .passNo(source.getPassNo())
                .cellCode(source.getCellCode())
                .deviceCode(source.getDeviceCode())
                .deviceName(source.getDeviceName())
                .coilerMethod(source.getCoilerMethod())
                .coilerMethodName(source.getCoilerMethodName())
                .coilNo(source.getCoilNo())
                .repeatProdNo(source.getRepeatProdNo())
                .colorNo(source.getColorNo())
                .remainingLength(source.getRemainingLength())
                .maxLength(source.getMaxLength())
                .build();
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
