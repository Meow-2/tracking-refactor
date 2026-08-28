package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.GratingPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearMode;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearSettings;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.WelderShearSettings;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.config.status.StatusPointGroup;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearDeviceRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCandidateRuntime;
import com.wisdri.tracking.domain.model.runtime.status.StatusCurrentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearKind;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.domain.model.tracking.status.StatusTrackingContext;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于颜色号或光栅占位和钢卷状态的通用剪切算法。
 */
@Component
public class ShearTrackingAlgorithmImpl implements TrackingAlgorithm<ShearResult> {
    /** 首次分切无法计算相邻剩余长度差时使用的长度。 */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 读取 shear/status 配置与运行态，并在结果入库前保存算法算出的 shear runtime。 */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    /** 输出触发、上下文、判型、刀次、长度和 runtime 提交步骤日志。 */
    @Resource
    private TrackingStepLogger trackingStepLogger;

    /** 提供独立的剪切 PostgreSQL 存储开关；关闭时不提交 shear runtime。 */
    @Resource
    private TrackingProperties trackingProperties;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.SHEAR == trackingType;
    }

    @Override
    public List<ShearResult> calculate(TrackingInput input) {
        List<ShearResult> results = new ArrayList<>();
        if (input == null || input.getLatestSnapshot() == null) {
            return results;
        }
        Optional<ShearTrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), TrackingType.SHEAR, ShearTrackingConfig.class);
        if (!configOptional.isPresent()) {
            trackingStepLogger.log(input, "剪切事件跳过", TrackingStepLogger.details(
                    "reason", "剪切配置不存在"));
            return results;
        }
        StatusTrackingContext context = input.getStatusContext();
        if (context == null) {
            trackingStepLogger.log(input, "剪切事件跳过", TrackingStepLogger.details(
                    "reason", "statusContext 不存在"));
            return results;
        }
        ShearTrackingSection tracking = configOptional.get().getTracking();
        synchronizeRuntimes(input, tracking, context);
        if (input.getPreviousSnapshot() == null) {
            trackingStepLogger.log(input, "剪切触发沿检查", TrackingStepLogger.details(
                    "previousAvailable", false,
                    "triggered", false));
            return results;
        }
        Map<String, ShearTrackingRuntime> workingRuntimes = new LinkedHashMap<>();
        calculatePoints(input, tracking, context, workingRuntimes,
                tracking.getUncoilerShearPoint(), true, results);
        calculatePoints(input, tracking, context, workingRuntimes,
                tracking.getCoilerShearPoint(), false, results);
        saveCalculatedRuntimes(input, workingRuntimes, results);
        return results;
    }

    /** 算法算出刀次后立即提交运行态，随后结果才进入数据库持久化流程。 */
    private void saveCalculatedRuntimes(TrackingInput input,
                                        Map<String, ShearTrackingRuntime> workingRuntimes,
                                        List<ShearResult> results) {
        if (!trackingProperties.shearStorageEnabled() || results.isEmpty()) {
            return;
        }
        Map<String, ShearTrackingRuntime> changed = new LinkedHashMap<>();
        for (ShearResult result : results) {
            ShearTrackingRuntime runtime = workingRuntimes.get(result.getDeviceCode());
            if (runtime != null) {
                changed.put(result.getDeviceCode(), runtime);
            }
        }
        for (ShearTrackingRuntime runtime : changed.values()) {
            runtime.setUpdatedAt(Instant.now());
            runtimeRepositoryDispatcher.saveRuntime(runtime);
        }
        trackingStepLogger.log(input, "剪切运行态提交", TrackingStepLogger.details(
                "resultCount", results.size(),
                "runtimeCount", changed.size()));
    }

    /**
     * 按配置顺序检查一侧的全部剪刀，仅接受“正常位 -> 非正常位”的离开沿。
     * 单点异常在此隔离，不阻断同一 MQTT 帧中其他剪刀的计算。
     */
    private void calculatePoints(TrackingInput input,
                                 ShearTrackingSection tracking,
                                 StatusTrackingContext context,
                                 Map<String, ShearTrackingRuntime> workingRuntimes,
                                 List<ShearPointConfig> points,
                                 boolean uncoilerSide,
                                 List<ShearResult> results) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            try {
                Boolean previous = booleanValue(input.getPreviousSnapshot(),
                        path(tracking, point.getName()));
                Boolean latest = booleanValue(input.getLatestSnapshot(),
                        path(tracking, point.getName()));
                boolean triggered = point.getNormalPos() != null
                        && point.getNormalPos().equals(previous)
                        && latest != null
                        && !point.getNormalPos().equals(latest);
                trackingStepLogger.log(input, "剪切触发沿检查", point.getName(),
                        TrackingStepLogger.details(
                                "deviceSide", uncoilerSide ? "UNCOILER" : "COILER",
                                "deviceCodes", deviceCodes(point),
                                "previous", previous,
                                "latest", latest,
                                "normalPos", point.getNormalPos(),
                                "triggered", triggered));
                if (!triggered) {
                    continue;
                }
                ShearResult result = calculatePoint(
                        input, tracking, context, workingRuntimes, point, uncoilerSide);
                if (result != null) {
                    results.add(result);
                }
            } catch (RuntimeException e) {
                trackingStepLogger.log(input, "剪切事件跳过", point == null ? null : point.getName(),
                        TrackingStepLogger.details(
                                "reason", e.getMessage(),
                                "deviceCodes", point == null ? null : deviceCodes(point)));
            }
        }
    }

    /**
     * 对一个有效触发沿完成设备解析、模式判型、物料归属、刀次和长度计算。
     * 返回值携带待持久化结果；runtime 先写入计算态，由 calculate 在返回结果前统一保存。
     */
    private ShearResult calculatePoint(TrackingInput input,
                                       ShearTrackingSection tracking,
                                       StatusTrackingContext context,
                                       Map<String, ShearTrackingRuntime> workingRuntimes,
                                       ShearPointConfig point,
                                       boolean uncoilerSide) {
        DeviceSide associatedSide = uncoilerSide ? DeviceSide.UNCOILER : DeviceSide.COILER;
        AssociatedEndpoint associated = associatedEndpoint(context, point, associatedSide);
        // 每个 device_code 使用独立副本，完成本帧计算后再统一覆盖正式 runtime。
        ShearTrackingRuntime runtime = workingRuntimes.computeIfAbsent(
                associated.deviceCode, code -> runtime(input.getUnitCode(), code, uncoilerSide));
        StatusCurrentRuntime opposite = context.getCurrent() == null ? null
                : context.getCurrent().get(uncoilerSide ? DeviceSide.COILER : DeviceSide.UNCOILER);
        if (opposite == null) {
            return skip(input, point, "对应设备或另一侧 current 不存在");
        }
        Endpoint por = uncoilerSide ? associated.endpoint : endpoint(opposite);
        Endpoint tr = uncoilerSide ? endpoint(opposite) : associated.endpoint;
        ShearKind kind = point.getShearSettings().getDefaultValue();
        if (!por.materialComplete() || !tr.materialComplete()) {
            return skip(input, point, "卷号、生产次数或剩余长度不完整");
        }
        String shearColor = null;
        if (kind == null && tracking.getMode() == ShearMode.CONTINUOUS) {
            if (por.colorNo == null || tr.colorNo == null) {
                return skip(input, point, "设备颜色号不完整");
            }
            shearColor = normalizedColor(PointReader.stringValue(
                    input.getLatestSnapshot(), path(tracking, point.getColorPoint())));
            if (shearColor == null) {
                return skip(input, point, "剪刀颜色点无效");
            }
        }
        if (kind == null) {
            if (tracking.getMode() == ShearMode.CONTINUOUS) {
                kind = uncoilerSide
                        ? classifyContinuousUncoiler(
                                por, tr, shearColor, tracking.getTailExperience())
                        : classifyContinuousCoiler(
                                input, tracking, runtime, point, por, tr, shearColor);
            } else if (tracking.getMode() == ShearMode.DISCONTINUOUS) {
                boolean occupied = allGratingsOccupied(
                        input.getLatestSnapshot(), tracking, point);
                kind = uncoilerSide
                        ? classifyDiscontinuousUncoiler(
                                por, tr, occupied, tracking.getTailExperience())
                        : classifyDiscontinuousCoiler(por, tr, occupied);
            } else {
                throw new IllegalArgumentException("不支持的剪切模式: " + tracking.getMode());
            }
        }
        Endpoint material = material(kind, uncoilerSide, por, tr);
        CounterDecision decision = counter(runtime, kind, uncoilerSide,
                por.remainingLength, tracking.getShearExperience(), true);
        LengthDecision lengthDecision = shearLength(
                input.getLatestSnapshot(), tracking, point, kind, uncoilerSide, decision);
        String typeCode = typeCode(point, kind);
        trackingStepLogger.log(input, "剪切类型判定", point.getName(),
                TrackingStepLogger.details(
                        "mode", tracking.getMode(),
                        "deviceCodes", deviceCodes(point),
                        "selectedDeviceCode", associated.deviceCode,
                        "deviceSource", associated.source,
                        "porCoilNo", por.coilNo,
                        "porColor", por.colorNo,
                        "trCoilNo", tr.coilNo,
                        "trColor", tr.colorNo,
                        "shearColor", shearColor,
                        "shearType", kind,
                        "shearTypeCode", typeCode));
        trackingStepLogger.log(input, "剪切刀次计算", point.getName(),
                TrackingStepLogger.details(
                        "inMatNo", material.coilNo,
                        "lastRemainLength", decision.previousRemainLength,
                        "currentRemainLength", por.remainingLength,
                        "delta", decision.delta,
                        "shearNo", decision.counter.getShearNo(),
                        "cutNo", decision.counter.getCutNo()));
        trackingStepLogger.log(input, "剪切长度计算", point.getName(),
                TrackingStepLogger.details(
                        "shearLength", lengthDecision.length,
                        "setNumber", lengthDecision.setNumber));
        Instant receivedAt = input.getLatestSnapshot().getReceivedAt();
        return ShearResult.builder()
                .unitCode(input.getUnitCode())
                .trackingType(TrackingType.SHEAR)
                .generatedAt(Instant.now())
                .receivedAt(receivedAt)
                .shearPointCode(point.getName())
                .deviceCode(associated.deviceCode)
                .shearKind(kind)
                .inMatNo(material.coilNo)
                .inMatNoProdNo(material.productNo)
                .shearType(typeCode)
                .shearTypeName(associated.deviceCode + "_" + kind.name().toLowerCase(Locale.ROOT))
                .shearLength(lengthDecision.length)
                .setNumber(lengthDecision.setNumber)
                .shearTime(receivedAt)
                .porCoilNo(por.coilNo)
                .porColorCode(por.colorNo)
                .trCoilNo(tr.coilNo)
                .trColorCode(tr.colorNo)
                .porRemainLength(por.remainingLength)
                .porMaxLength(por.maxLength)
                .trRemainLength(tr.remainingLength)
                .trMaxLength(tr.maxLength)
                .cutNo(decision.counter.getCutNo())
                .shearNo(decision.counter.getShearNo())
                .build();
    }

    /**
     * 开卷机侧判型：两端和剪刀处颜色决定切头/切尾，颜色相同时再用剩余长度区分切尾和分切。
     */
    private ShearKind classifyContinuousUncoiler(Endpoint por,
                                                 Endpoint tr,
                                                 String shearColor,
                                                 BigDecimal tailExperience) {
        boolean sameEnds = colorsEqual(por.colorNo, tr.colorNo);
        if (!sameEnds && colorsEqual(shearColor, por.colorNo)) {
            return ShearKind.HEAD;
        }
        if ((sameEnds && por.remainingLength.compareTo(tailExperience) <= 0)
                || (!sameEnds && !colorsEqual(shearColor, por.colorNo))) {
            return ShearKind.TAIL;
        }
        return ShearKind.SLICE;
    }

    /**
     * 卷取机侧判型：剪刀处与卷取机颜色相同为分切；否则按焊缝前设定片数划分切尾和切头。
     */
    private ShearKind classifyContinuousCoiler(TrackingInput input,
                                               ShearTrackingSection tracking,
                                               ShearTrackingRuntime runtime,
                                               ShearPointConfig point,
                                               Endpoint por,
                                               Endpoint tr,
                                               String shearColor) {
        if (colorsEqual(tr.colorNo, shearColor)) {
            return ShearKind.SLICE;
        }
        WelderShearSettings front = point.getShearSettings().getFrontWelder();
        int samplePieces = integerValue(input.getLatestSnapshot(), tracking, front.getSamplePieces());
        int scrapPieces = integerValue(input.getLatestSnapshot(), tracking, front.getScrapPieces());
        int frontWelderPieces = welderPieces(
                input.getLatestSnapshot(), tracking, point.getShearSettings()) / 2;
        int tailLimit = samplePieces + scrapPieces + frontWelderPieces + 1;
        CounterDecision nextTail = counter(runtime, ShearKind.TAIL, false,
                por.remainingLength, tracking.getShearExperience(), false);
        return nextTail.counter.getCutNo() <= tailLimit ? ShearKind.TAIL : ShearKind.HEAD;
    }

    /**
     * 非连续线开卷机侧判型：同卷按剩余长度区分切尾和分切，不同卷按光栅是否全部占位区分切头和切尾。
     */
    private ShearKind classifyDiscontinuousUncoiler(Endpoint por,
                                                    Endpoint tr,
                                                    boolean allGratingsOccupied,
                                                    BigDecimal tailExperience) {
        if (Objects.equals(por.coilNo, tr.coilNo)) {
            return por.remainingLength.compareTo(tailExperience) < 0
                    ? ShearKind.TAIL : ShearKind.SLICE;
        }
        return allGratingsOccupied ? ShearKind.HEAD : ShearKind.TAIL;
    }

    /** 非连续线卷取机侧判型：同卷为分切，不同卷按光栅是否全部占位区分切尾和切头。 */
    private ShearKind classifyDiscontinuousCoiler(Endpoint por,
                                                  Endpoint tr,
                                                  boolean allGratingsOccupied) {
        if (Objects.equals(por.coilNo, tr.coilNo)) {
            return ShearKind.SLICE;
        }
        return allGratingsOccupied ? ShearKind.TAIL : ShearKind.HEAD;
    }

    /** 所有配置光栅的当前值均等于各自 hasCoil 时，认为剪刀到设备之间连续占位。 */
    private boolean allGratingsOccupied(PointSnapshot snapshot,
                                        ShearTrackingSection tracking,
                                        ShearPointConfig point) {
        List<GratingPointConfig> gratings = point.getGratingPoints();
        if (gratings == null || gratings.isEmpty()) {
            throw new IllegalArgumentException("grating_points 不能为空: " + point.getName());
        }
        for (GratingPointConfig grating : gratings) {
            if (grating == null || grating.getHasCoil() == null) {
                throw new IllegalArgumentException("光栅配置无效: " + point.getName());
            }
            String pointPath = path(tracking, grating.getName());
            Boolean value = booleanValue(snapshot, pointPath);
            if (value == null) {
                throw new IllegalArgumentException("光栅点位值不存在: " + pointPath);
            }
            if (!grating.getHasCoil().equals(value)) {
                return false;
            }
        }
        return true;
    }

    /** 根据剪刀所在设备侧和逻辑判型，选择本条记录归属的投入物料。 */
    private Endpoint material(ShearKind kind,
                              boolean uncoilerSide,
                              Endpoint por,
                              Endpoint tr) {
        if (uncoilerSide) {
            return kind == ShearKind.TAIL ? tr : por;
        }
        return kind == ShearKind.HEAD ? por : tr;
    }

    /**
     * 计算本刀长度及 setNumber；分切长度来自相邻剩余长度差，其他类型按生产线模式读取配置。
     */
    private LengthDecision shearLength(PointSnapshot snapshot,
                                       ShearTrackingSection tracking,
                                       ShearPointConfig point,
                                       ShearKind kind,
                                       boolean uncoilerSide,
                                       CounterDecision decision) {
        if (kind == ShearKind.SLICE) {
            BigDecimal length = decision.counter.getCutNo() == 1 || decision.delta == null
                    ? ZERO : decision.delta.max(ZERO);
            return new LengthDecision(length, null);
        }
        ShearSettings settings = point.getShearSettings();
        if (uncoilerSide || tracking.getMode() == ShearMode.DISCONTINUOUS) {
            CutSetting cutSetting = kind == ShearKind.HEAD ? settings.getHead() : settings.getTail();
            return new LengthDecision(
                    requiredDecimal(snapshot, tracking, cutSetting.getLength()),
                    integerValue(snapshot, tracking, cutSetting.getNumber()));
        }
        WelderShearSettings welder = kind == ShearKind.TAIL
                ? settings.getFrontWelder() : settings.getBehindWelder();
        int totalWelderPieces = welderPieces(snapshot, tracking, settings);
        int frontWelderPieces = totalWelderPieces / 2;
        int allocatedWelderPieces = kind == ShearKind.TAIL
                ? frontWelderPieces : totalWelderPieces - frontWelderPieces;
        int setNumber = integerValue(snapshot, tracking, welder.getSamplePieces())
                + integerValue(snapshot, tracking, welder.getScrapPieces())
                + allocatedWelderPieces;
        return new LengthDecision(welderLength(snapshot, tracking, welder, kind, decision), setNumber);
    }

    /**
     * 读取焊缝废料总片数。未配置时按 0 兼容旧机组；负数视为无效点位值。
     */
    private int welderPieces(PointSnapshot snapshot,
                             ShearTrackingSection tracking,
                             ShearSettings settings) {
        if (settings.getWelderPieces() == null) {
            return 0;
        }
        int pieces = integerValue(snapshot, tracking, settings.getWelderPieces());
        if (pieces < 0) {
            throw new IllegalArgumentException("welder_pieces 不能小于 0");
        }
        return pieces;
    }

    /**
     * 读取飞剪单片长度：优先使用公共 length，否则按当前片序选择 sampleLength 或 scrapLength。
     */
    private BigDecimal welderLength(PointSnapshot snapshot,
                                    ShearTrackingSection tracking,
                                    WelderShearSettings welder,
                                    ShearKind kind,
                                    CounterDecision decision) {
        if (welder.getLength() != null) {
            return requiredDecimal(snapshot, tracking, welder.getLength());
        }
        int cutNo = decision.counter.getCutNo();
        int pieceNo = kind == ShearKind.TAIL ? cutNo - 1 : cutNo;
        int samplePieces = integerValue(snapshot, tracking, welder.getSamplePieces());
        PointConfig lengthPoint = pieceNo <= samplePieces
                ? welder.getSampleLength() : welder.getScrapLength();
        return requiredDecimal(snapshot, tracking, lengthPoint);
    }

    /**
     * 计算下一刀计数。apply=false 仅用于卷取机侧预判下一刀类型，不写入计算态 runtime；
     * apply=true 写入 calculate 阶段副本，并在 calculate 返回结果前正式提交。
     */
    private CounterDecision counter(ShearTrackingRuntime runtime,
                                    ShearKind kind,
                                    boolean uncoilerSide,
                                    BigDecimal porRemainLength,
                                    BigDecimal experience,
                                    boolean apply) {
        ShearDeviceRuntime materialRuntime = materialRuntime(runtime, kind, uncoilerSide);
        ShearCounterRuntime previous = materialRuntime.counter(kind);
        BigDecimal previousRemain = previous == null ? null : previous.getLastPorRemainLength();
        BigDecimal delta = previousRemain == null ? null : previousRemain.subtract(porRemainLength);
        ShearCounterRuntime next;
        if (previous == null || previous.getShearNo() == null || previous.getCutNo() == null
                || previous.getLastPorRemainLength() == null) {
            next = ShearCounterRuntime.builder().shearNo(1).cutNo(1)
                    .lastPorRemainLength(porRemainLength).build();
        } else if (delta.signum() >= 0 && delta.compareTo(experience) < 0) {
            next = ShearCounterRuntime.builder().shearNo(previous.getShearNo())
                    .cutNo(previous.getCutNo() + 1).lastPorRemainLength(porRemainLength).build();
        } else {
            next = ShearCounterRuntime.builder().shearNo(previous.getShearNo() + 1)
                    .cutNo(1).lastPorRemainLength(porRemainLength).build();
        }
        if (apply) {
            materialRuntime.setCounter(kind, next);
        }
        return new CounterDecision(next, previousRemain, delta);
    }

    private ShearDeviceRuntime materialRuntime(ShearTrackingRuntime runtime,
                                                ShearKind kind,
                                                boolean uncoilerSide) {
        boolean belongsToUncoiler = uncoilerSide
                ? kind != ShearKind.TAIL : kind == ShearKind.HEAD;
        ShearDeviceRuntime material = belongsToUncoiler
                ? runtime.getUncoiler() : runtime.getCoiler();
        if (material == null) {
            throw new IllegalStateException("剪切运行态设备绑定不存在: "
                    + (belongsToUncoiler ? "uncoiler" : "coiler"));
        }
        return material;
    }

    /** 每帧刷新所有配置代码的两侧设备绑定，即使本帧没有剪切触发。 */
    private void synchronizeRuntimes(TrackingInput input,
                                     ShearTrackingSection tracking,
                                     StatusTrackingContext context) {
        if (!trackingProperties.shearStorageEnabled()) {
            return;
        }
        Map<String, String> deviceNames = statusDeviceNames(input.getUnitCode());
        synchronizePoints(input, context, tracking.getUncoilerShearPoint(),
                true, deviceNames);
        synchronizePoints(input, context, tracking.getCoilerShearPoint(),
                false, deviceNames);
    }

    private void synchronizePoints(TrackingInput input,
                                   StatusTrackingContext context,
                                   List<ShearPointConfig> points,
                                   boolean uncoilerSide,
                                   Map<String, String> deviceNames) {
        if (points == null) {
            return;
        }
        for (ShearPointConfig point : points) {
            for (String deviceCode : deviceCodes(point)) {
                ShearTrackingRuntime runtime = runtime(
                        input.getUnitCode(), deviceCode, uncoilerSide);
                ShearDeviceRuntime previousUncoiler = runtime.getUncoiler();
                ShearDeviceRuntime previousCoiler = runtime.getCoiler();
                StatusCurrentRuntime opposite = context.getCurrent() == null ? null
                        : context.getCurrent().get(uncoilerSide
                                ? DeviceSide.COILER : DeviceSide.UNCOILER);
                ShearDeviceRuntime uncoiler = uncoilerSide
                        ? configuredDevice(context, DeviceSide.UNCOILER, deviceCode,
                                deviceNames.get(deviceCode), previousUncoiler, true)
                        : currentDevice(DeviceSide.UNCOILER, opposite, previousUncoiler, false);
                ShearDeviceRuntime coiler = uncoilerSide
                        ? currentDevice(DeviceSide.COILER, opposite, previousCoiler, false)
                        : configuredDevice(context, DeviceSide.COILER, deviceCode,
                                deviceNames.get(deviceCode), previousCoiler, true);
                if (Objects.equals(previousUncoiler, uncoiler)
                        && Objects.equals(previousCoiler, coiler)) {
                    continue;
                }
                runtime.setUncoiler(uncoiler);
                runtime.setCoiler(coiler);
                runtime.setUpdatedAt(Instant.now());
                runtimeRepositoryDispatcher.saveRuntime(runtime);
            }
        }
    }

    private ShearDeviceRuntime configuredDevice(StatusTrackingContext context,
                                                 DeviceSide side,
                                                 String deviceCode,
                                                 String deviceName,
                                                 ShearDeviceRuntime previous,
                                                 boolean sliceEnabled) {
        StatusCurrentRuntime current = context.getCurrent() == null
                ? null : context.getCurrent().get(side);
        if (current != null && deviceCode.equals(current.getDeviceCode())) {
            return device(side, current.getRunning(), deviceCode,
                    current.getDeviceName() == null ? deviceName : current.getDeviceName(),
                    current.getCoilNo(), current.getProductNo(), current.getColorNo(),
                    current.getRemainingLength(), current.getMaxLength(), previous, sliceEnabled);
        }
        StatusCandidateRuntime candidate = context.getCandidates() == null
                ? null : context.getCandidates().get(deviceCode);
        if (candidate == null) {
            return device(side, false, deviceCode, deviceName,
                    null, null, null, null, null, previous, sliceEnabled);
        }
        String resolvedDeviceName = candidate.getDeviceName() == null
                ? deviceName : candidate.getDeviceName();
        return device(side, Boolean.TRUE.equals(candidate.getDataComplete()), deviceCode, resolvedDeviceName,
                candidate.getCoilNo(), candidate.getProductNo(), candidate.getColorNo(),
                last(candidate.getLengths()), candidate.getMaxLength(), previous, sliceEnabled);
    }

    private ShearDeviceRuntime currentDevice(DeviceSide side,
                                              StatusCurrentRuntime current,
                                              ShearDeviceRuntime previous,
                                              boolean sliceEnabled) {
        if (current == null) {
            return device(side, false, null, null,
                    null, null, null, null, null, previous, sliceEnabled);
        }
        return device(side, current.getRunning(), current.getDeviceCode(), current.getDeviceName(),
                current.getCoilNo(), current.getProductNo(), current.getColorNo(),
                current.getRemainingLength(), current.getMaxLength(), previous, sliceEnabled);
    }

    private ShearDeviceRuntime device(DeviceSide side,
                                      Boolean running,
                                      String deviceCode,
                                      String deviceName,
                                      String coilNo,
                                      Integer productNo,
                                      String colorNo,
                                      BigDecimal remainingLength,
                                      BigDecimal maxLength,
                                      ShearDeviceRuntime previous,
                                      boolean sliceEnabled) {
        boolean sameMaterial = previous != null
                && Objects.equals(previous.getDeviceCode(), deviceCode)
                && Objects.equals(previous.getCoilNo(), coilNo)
                && Objects.equals(previous.getProductNo(), productNo);
        return ShearDeviceRuntime.builder()
                .side(side)
                .running(Boolean.TRUE.equals(running))
                .deviceCode(deviceCode)
                .deviceName(deviceName)
                .coilNo(coilNo)
                .productNo(productNo)
                .colorNo(normalizedColor(colorNo))
                .remainingLength(remainingLength)
                .maxLength(maxLength)
                .head(side == DeviceSide.UNCOILER
                        ? retainedCounter(previous, ShearKind.HEAD, sameMaterial) : null)
                .slice(sliceEnabled
                        ? retainedCounter(previous, ShearKind.SLICE, sameMaterial) : null)
                .tail(side == DeviceSide.COILER
                        ? retainedCounter(previous, ShearKind.TAIL, sameMaterial) : null)
                .build();
    }

    private ShearCounterRuntime retainedCounter(ShearDeviceRuntime previous,
                                                ShearKind kind,
                                                boolean sameMaterial) {
        ShearCounterRuntime counter = sameMaterial && previous != null
                ? previous.counter(kind) : null;
        return counter == null ? ShearCounterRuntime.builder().build() : copyCounter(counter);
    }

    private Map<String, String> statusDeviceNames(String unitCode) {
        Map<String, String> names = new LinkedHashMap<>();
        runtimeRepositoryDispatcher.findConfigAs(
                unitCode, TrackingType.STATUS, StatusTrackingConfig.class)
                .map(StatusTrackingConfig::getTracking)
                .ifPresent(tracking -> {
                    if (tracking.getPoints() != null) {
                        for (StatusPointGroup point : tracking.getPoints()) {
                            if (point != null && point.getCode() != null) {
                                names.put(point.getCode(), point.getName());
                            }
                        }
                    }
                });
        return names;
    }

    /** 获取指定 device_code runtime 的深拷贝；不存在时创建该剪刀的空计数态。 */
    private ShearTrackingRuntime runtime(String unitCode, String deviceCode, boolean uncoilerSide) {
        return runtimeRepositoryDispatcher.findRuntimeAs(
                unitCode, TrackingType.SHEAR, deviceCode, ShearTrackingRuntime.class)
                .map(this::copyRuntime)
                .orElseGet(() -> emptyRuntime(unitCode, deviceCode, null, uncoilerSide));
    }

    private ShearTrackingRuntime emptyRuntime(String unitCode,
                                               String deviceCode,
                                               String deviceName,
                                               boolean uncoilerSide) {
        ShearDeviceRuntime uncoiler = device(DeviceSide.UNCOILER, false,
                uncoilerSide ? deviceCode : null, uncoilerSide ? deviceName : null,
                null, null, null, null, null, null, uncoilerSide);
        ShearDeviceRuntime coiler = device(DeviceSide.COILER, false,
                uncoilerSide ? null : deviceCode, uncoilerSide ? null : deviceName,
                null, null, null, null, null, null, !uncoilerSide);
        return ShearTrackingRuntime.builder()
                .unitCode(unitCode)
                .trackingType(TrackingType.SHEAR)
                .deviceCode(deviceCode)
                .uncoiler(uncoiler)
                .coiler(coiler)
                .build();
    }

    private ShearTrackingRuntime copyRuntime(ShearTrackingRuntime source) {
        return ShearTrackingRuntime.builder()
                .unitCode(source.getUnitCode())
                .trackingType(TrackingType.SHEAR)
                .deviceCode(source.getDeviceCode())
                .updatedAt(source.getUpdatedAt())
                .uncoiler(copyDevice(source.getUncoiler()))
                .coiler(copyDevice(source.getCoiler()))
                .build();
    }

    private ShearDeviceRuntime copyDevice(ShearDeviceRuntime source) {
        if (source == null) {
            return null;
        }
        return ShearDeviceRuntime.builder()
                .side(source.getSide())
                .running(source.getRunning())
                .deviceCode(source.getDeviceCode())
                .deviceName(source.getDeviceName())
                .coilNo(source.getCoilNo())
                .productNo(source.getProductNo())
                .colorNo(source.getColorNo())
                .remainingLength(source.getRemainingLength())
                .maxLength(source.getMaxLength())
                .head(copyCounter(source.getHead()))
                .slice(copyCounter(source.getSlice()))
                .tail(copyCounter(source.getTail()))
                .build();
    }

    private ShearCounterRuntime copyCounter(ShearCounterRuntime source) {
        return source == null ? null : ShearCounterRuntime.builder()
                .shearNo(source.getShearNo())
                .cutNo(source.getCutNo())
                .lastPorRemainLength(source.getLastPorRemainLength())
                .build();
    }

    private Endpoint endpoint(StatusCandidateRuntime candidate) {
        return new Endpoint(candidate.getCoilNo(), text(candidate.getProductNo()),
                normalizedColor(candidate.getColorNo()), last(candidate.getLengths()), candidate.getMaxLength());
    }

    private Endpoint endpoint(StatusCurrentRuntime current) {
        return new Endpoint(current.getCoilNo(), text(current.getProductNo()),
                normalizedColor(current.getColorNo()), current.getRemainingLength(), current.getMaxLength());
    }

    /**
     * 在配置设备范围内依次匹配该侧 current；没有任何匹配时固定回退到列表最后一个 candidate。
     */
    private AssociatedEndpoint associatedEndpoint(StatusTrackingContext context,
                                                  ShearPointConfig point,
                                                  DeviceSide side) {
        List<String> codes = deviceCodes(point);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("device_codes 为空");
        }
        StatusCurrentRuntime current = context.getCurrent() == null
                ? null : context.getCurrent().get(side);
        if (current != null) {
            for (String code : codes) {
                if (code.equals(current.getDeviceCode())) {
                    return new AssociatedEndpoint(code, endpoint(current), "CURRENT");
                }
            }
        }
        String fallbackCode = codes.get(codes.size() - 1);
        StatusCandidateRuntime fallback = context.getCandidates() == null
                ? null : context.getCandidates().get(fallbackCode);
        if (fallback == null) {
            throw new IllegalArgumentException("回退设备 candidate 不存在: " + fallbackCode);
        }
        if (Boolean.FALSE.equals(fallback.getDataComplete())) {
            throw new IllegalArgumentException("回退设备当前帧卷号或剩余长度无效: " + fallbackCode);
        }
        return new AssociatedEndpoint(fallbackCode, endpoint(fallback), "LAST_CONFIGURED_CANDIDATE");
    }

    /** 读取有序设备范围；复数配置优先，单数配置作为向后兼容。 */
    private List<String> deviceCodes(ShearPointConfig point) {
        if (point != null && point.getDeviceCodes() != null && !point.getDeviceCodes().isEmpty()) {
            return point.getDeviceCodes();
        }
        return point == null || point.getDeviceCode() == null
                ? Collections.emptyList() : Collections.singletonList(point.getDeviceCode());
    }

    private BigDecimal last(List<BigDecimal> values) {
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
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

    private int integerValue(PointSnapshot snapshot, ShearTrackingSection tracking, PointConfig point) {
        return requiredDecimal(snapshot, tracking, point).intValueExact();
    }

    private BigDecimal requiredDecimal(PointSnapshot snapshot,
                                       ShearTrackingSection tracking,
                                       PointConfig point) {
        BigDecimal value = PointReader.decimalValue(snapshot, path(tracking, point));
        if (value == null) {
            throw new IllegalArgumentException("点位值不存在: " + (point == null ? null : point.getName()));
        }
        return value;
    }

    private String path(ShearTrackingSection tracking, PointConfig point) {
        return path(tracking, point == null ? null : point.getName());
    }

    private String path(ShearTrackingSection tracking, String point) {
        return PointReader.pathResolve(tracking.getPointPrefix(), point);
    }

    private ShearResult skip(TrackingInput input, ShearPointConfig point, String reason) {
        trackingStepLogger.log(input, "剪切事件跳过", point.getName(), TrackingStepLogger.details(
                "reason", reason,
                "deviceCodes", deviceCodes(point)));
        return null;
    }

    private boolean colorsEqual(String left, String right) {
        return left != null && left.equals(right);
    }

    private String normalizedColor(String value) {
        String trimmed = trimInvisible(value);
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private String trimInvisible(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && invisible(value.charAt(start))) {
            start++;
        }
        while (end > start && invisible(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean invisible(char value) {
        return Character.isWhitespace(value)
                || Character.isSpaceChar(value)
                || Character.isISOControl(value)
                || Character.getType(value) == Character.FORMAT;
    }

    private Boolean booleanValue(PointSnapshot snapshot, String pointPath) {
        Object value = PointReader.rawValue(snapshot, pointPath);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String normalized = String.valueOf(value).trim();
        if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized)) {
            return Boolean.TRUE;
        }
        if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("剪切信号无法转换为 boolean: " + pointPath + "=" + value);
    }

    /** 配置设备解析后的物料端点及选择来源。 */
    private static class AssociatedEndpoint {
        /** 实际命中的 status 设备代码。 */
        private final String deviceCode;
        /** 后续颜色、物料、长度判定使用的设备快照。 */
        private final Endpoint endpoint;
        /** CURRENT 或 LAST_CONFIGURED_CANDIDATE。 */
        private final String source;

        private AssociatedEndpoint(String deviceCode, Endpoint endpoint, String source) {
            this.deviceCode = deviceCode;
            this.endpoint = endpoint;
            this.source = source;
        }
    }

    /** 从 status candidate/current 统一抽取的单侧设备物料快照。 */
    private static class Endpoint {
        /** 该设备上的钢卷号。 */
        private final String coilNo;
        /** 该钢卷本次生产次数的字符串表示。 */
        private final String productNo;
        /** 去除不可见字符并完成数值规范化后的颜色号。 */
        private final String colorNo;
        /** 触发时刻该设备的剩余长度。 */
        private final BigDecimal remainingLength;
        /** status 上下文提供的该设备最大长度。 */
        private final BigDecimal maxLength;

        private Endpoint(String coilNo,
                         String productNo,
                         String colorNo,
                         BigDecimal remainingLength,
                         BigDecimal maxLength) {
            this.coilNo = coilNo;
            this.productNo = productNo;
            this.colorNo = colorNo;
            this.remainingLength = remainingLength;
            this.maxLength = maxLength;
        }

        private boolean materialComplete() {
            return coilNo != null && !coilNo.trim().isEmpty()
                    && productNo != null && !productNo.trim().isEmpty()
                    && remainingLength != null;
        }
    }

    /** 单次刀次推演结果，供结果构建、长度计算和步骤日志共同使用。 */
    private static class CounterDecision {
        /** 推演后的 shearNo、cutNo 和本次剩余长度。 */
        private final ShearCounterRuntime counter;
        /** 上一次成功记录的开卷机剩余长度；首次为 null。 */
        private final BigDecimal previousRemainLength;
        /** previousRemainLength - 当前开卷机剩余长度；首次为 null。 */
        private final BigDecimal delta;

        private CounterDecision(ShearCounterRuntime counter,
                                BigDecimal previousRemainLength,
                                BigDecimal delta) {
            this.counter = counter;
            this.previousRemainLength = previousRemainLength;
            this.delta = delta;
        }
    }

    /** 本刀最终写库的长度及其设定数量。 */
    private static class LengthDecision {
        /** 本刀剪切长度。 */
        private final BigDecimal length;
        /** 长度对应的设定数量；切头切尾取配置数量，分切没有对应设定数量。 */
        private final Integer setNumber;

        private LengthDecision(BigDecimal length, Integer setNumber) {
            this.length = length;
            this.setNumber = setNumber;
        }
    }
}
