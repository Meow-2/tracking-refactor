package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearMode;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.shear.ShearRuntimeCommit;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDecision;
import com.wisdri.tracking.domain.model.tracking.shear.ShearDeviceSnapshot;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.tracking.shear.ShearDecisionService;
import com.wisdri.tracking.domain.service.tracking.shear.ShearDeviceResolver;
import com.wisdri.tracking.domain.service.tracking.shear.ShearRuntimeService;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 剪切算法应用入口：检查帧触发沿并协调设备解析、业务判定和 runtime 提交。
 * 具体判型、设备匹配、计数推演和长度计算分别由剪切领域服务负责。
 */
@Component
public class ShearTrackingAlgorithmImpl implements TrackingAlgorithm<ShearResult> {
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;
    @Resource
    private TrackingStepLogger trackingStepLogger;
    @Resource
    private TrackingProperties trackingProperties;
    @Resource
    private ShearDeviceResolver shearDeviceResolver;
    @Resource
    private ShearDecisionService shearDecisionService;
    @Resource
    private ShearRuntimeService shearRuntimeService;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.SHEAR == trackingType;
    }

    /**
     * 计算单帧内全部剪切点；所有设备状态均在副本上更新，按存储开关决定何时正式提交。
     */
    @Override
    public List<ShearResult> calculate(TrackingInput input) {
        List<ShearResult> results = new ArrayList<>();
        if (input == null || input.getLatestSnapshot() == null) {
            return results;
        }
        ShearTrackingConfig config = runtimeRepositoryDispatcher.findConfigAs(
                        input.getUnitCode(), TrackingType.SHEAR, ShearTrackingConfig.class)
                .orElse(null);
        if (config == null || config.getTracking() == null) {
            skip(input, null, "剪切配置不存在");
            return results;
        }
        StatusTrackingContext context = input.getStatusContext();
        if (context == null) {
            skip(input, null, "statusContext 不存在");
            return results;
        }
        ShearTrackingSection tracking = config.getTracking();
        StatusTrackingConfig statusConfig = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), TrackingType.STATUS, StatusTrackingConfig.class).orElse(null);
        if (statusConfig == null || statusConfig.getTracking() == null
                || statusConfig.getTracking().getPoints() == null) {
            skip(input, null, "status 设备配置不存在，无法确定候选设备顺序");
            return results;
        }
        // CSL1 重卷机组的空卷及同卷识别规则只适用于非连续线。
        boolean csl1Discontinuous = "CSL1".equalsIgnoreCase(input.getUnitCode())
                && tracking.getMode() == ShearMode.DISCONTINUOUS;
        List<ShearDeviceSnapshot> devices = shearDeviceResolver.resolveCandidates(
                context, statusConfig, csl1Discontinuous);
        ShearRuntimeCommit commit = shearRuntimeService.prepare(input.getUnitCode(), devices, tracking);
        Set<String> processedTriggerDevices = new HashSet<>();
        processPoints(input, tracking, devices, commit,
                tracking.getUncoilerShearPoint(), true, csl1Discontinuous, processedTriggerDevices, results);
        processPoints(input, tracking, devices, commit,
                tracking.getCoilerShearPoint(), false, csl1Discontinuous, processedTriggerDevices, results);

        if (results.isEmpty() || !trackingProperties.shearStorageEnabled()) {
            shearRuntimeService.commitCalculated(commit);
        } else {
            for (ShearResult result : results) {
                result.setRuntimeCommit(commit);
            }
        }
        return results;
    }

    /**
     * 检查一侧的全部剪切点。每个点分别捕获数据或配置异常，保证同帧其他剪刀继续处理。
     */
    private void processPoints(TrackingInput input,
                               ShearTrackingSection tracking,
                               List<ShearDeviceSnapshot> devices,
                               ShearRuntimeCommit commit,
                               List<ShearPointConfig> points,
                               boolean uncoilerSide,
                               boolean compareCommonCoilPrefix,
                               Set<String> processedTriggerDevices,
                               List<ShearResult> results) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            if (point == null || point.getShearSettings() == null) {
                skip(input, point, "剪切点配置不存在");
                continue;
            }
            try {
                if (!detectTrigger(input.getPreviousSnapshot(), input.getLatestSnapshot(), tracking, point)) {
                    continue;
                }
                boolean requireCoil = !uncoilerSide && tracking.getMode()
                        == com.wisdri.tracking.domain.model.config.shear.ShearMode.CONTINUOUS;
                ShearDeviceSnapshot shearDevice = shearDeviceResolver.resolveShearDevice(
                        point, devices, requireCoil);
                if (shearDevice == null) {
                    skip(input, point, "剪切设备未在当前 candidates 中找到");
                    continue;
                }
                ShearTrackingRuntime triggerRuntime = commit.getRuntimes().get(shearDevice.getDeviceCode());
                Instant receivedAt = input.getLatestSnapshot().getReceivedAt();
                if (shearRuntimeService.alreadyPersisted(triggerRuntime, receivedAt)
                        || processedTriggerDevices.contains(shearDevice.getDeviceCode())) {
                    skip(input, point, "该设备在当前触发帧已处理或已成功入库");
                    continue;
                }
                ShearDecision decision = shearDecisionService.decide(input.getLatestSnapshot(),
                        tracking, point, uncoilerSide, compareCommonCoilPrefix, devices,
                        commit.getRuntimes());
                ShearResult result = buildResult(input, point, decision);
                shearRuntimeService.stage(commit, decision.getInMatDevice(), decision.getKind(),
                        decision.getNextCounter(), decision.getShearDevice(), receivedAt);
                processedTriggerDevices.add(shearDevice.getDeviceCode());
                results.add(result);
                logDecision(input, point, decision);
            } catch (RuntimeException e) {
                skip(input, point, e.getMessage());
            }
        }
    }

    /** 只有“上一帧为正常位、当前帧有效且离开正常位”才构成一次剪切事件。 */
    private boolean detectTrigger(PointSnapshot previous,
                                  PointSnapshot latest,
                                  ShearTrackingSection tracking,
                                  ShearPointConfig point) {
        if (previous == null || point.getNormalPos() == null) {
            return false;
        }
        String path = PointReader.pathResolve(tracking.getPointPrefix(), point.getName());
        Boolean before = shearDeviceResolver.booleanValue(previous, path);
        Boolean now = shearDeviceResolver.booleanValue(latest, path);
        return point.getNormalPos().equals(before) && now != null && !point.getNormalPos().equals(now);
    }

    /** 将完整判定映射为剪切表领域结果，并保留 runtime key 与触发时间。 */
    private ShearResult buildResult(TrackingInput input,
                                   ShearPointConfig point,
                                   ShearDecision decision) {
        ShearDeviceSnapshot material = decision.getInMatDevice();
        ShearDeviceSnapshot shear = decision.getShearDevice();
        Instant receivedAt = input.getLatestSnapshot().getReceivedAt();
        ShearKind kind = decision.getKind();
        return ShearResult.builder()
                .unitCode(input.getUnitCode())
                .trackingType(TrackingType.SHEAR)
                .generatedAt(Instant.now())
                .receivedAt(receivedAt)
                .shearPointCode(point.getName())
                .deviceCode(shear.getDeviceCode())
                .shearKind(kind)
                .inMatNo(material.getCoilNo())
                .repeatProdNo(String.valueOf(material.getRepeatProdNo()))
                .shearType(typeCode(point, kind))
                .shearTypeName(shear.getDeviceCode() + "_" + kind.name().toLowerCase(Locale.ROOT))
                .shearLength(decision.getShearLength())
                .setNumber(decision.getSetNumber())
                .cutNo(decision.getNextCounter().getCutNo())
                .shearNo(decision.getNextCounter().getShearNo())
                .inMatDeviceCode(material.getDeviceCode())
                .inMatDeviceColorNo(material.getColorNo())
                .inMatDeviceRemainLength(material.getRemainingLength())
                .inMatDeviceMaxLength(material.getMaxLength())
                .shearDeviceCoilNo(shear.getCoilNo())
                .shearDeviceColorNo(shear.getColorNo())
                .shearDeviceRemainLength(shear.getRemainingLength())
                .shearDeviceMaxLength(shear.getMaxLength())
                .shearTime(receivedAt)
                .build();
    }

    /** 数据库批量持久化成功后才提交计数和设备级幂等时间。 */
    @Override
    public void afterPersist(TrackingInput input, List<ShearResult> results) {
        if (!trackingProperties.shearStorageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        ShearRuntimeCommit commit = results.get(0).getRuntimeCommit();
        if (commit != null) {
            shearRuntimeService.commitPersisted(commit);
            trackingStepLogger.log(input, "剪切 runtime 提交", TrackingStepLogger.details(
                    "resultCount", results.size(), "runtimeCount", commit.getRuntimes().size()));
        }
    }

    private String typeCode(ShearPointConfig point, ShearKind kind) {
        switch (kind) {
            case HEAD:
                return point.getTypeCodes().getHead();
            case TAIL:
                return point.getTypeCodes().getTail();
            default:
                return point.getTypeCodes().getSlice();
        }
    }

    private void logDecision(TrackingInput input, ShearPointConfig point, ShearDecision decision) {
        trackingStepLogger.log(input, "剪切类型判定", point.getName(), TrackingStepLogger.details(
                "shearDevice", decision.getShearDevice().getDeviceCode(),
                "inMatDevice", decision.getInMatDevice().getDeviceCode(),
                "shearType", decision.getKind()));
        trackingStepLogger.log(input, "剪切刀次和长度计算", point.getName(), TrackingStepLogger.details(
                "shearNo", decision.getNextCounter().getShearNo(),
                "cutNo", decision.getNextCounter().getCutNo(),
                "firstCut", decision.isFirstCut(),
                "delta", decision.getLengthDelta(),
                "shearLength", decision.getShearLength(),
                "setNumber", decision.getSetNumber()));
    }

    private void skip(TrackingInput input, ShearPointConfig point, String reason) {
        trackingStepLogger.log(input, "剪切事件跳过", point == null ? null : point.getName(),
                TrackingStepLogger.details("reason", reason));
    }
}
