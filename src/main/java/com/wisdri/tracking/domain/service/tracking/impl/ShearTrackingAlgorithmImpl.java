package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.shear.CutSetting;
import com.wisdri.tracking.domain.model.config.shear.ShearPointConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearSettings;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingConfig;
import com.wisdri.tracking.domain.model.config.shear.ShearTrackingSection;
import com.wisdri.tracking.domain.model.config.shear.WelderShearSettings;
import com.wisdri.tracking.domain.model.config.status.DeviceSide;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.shear.ShearCounterRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearMaterialRuntime;
import com.wisdri.tracking.domain.model.runtime.shear.ShearPointRuntime;
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
import com.wisdri.tracking.domain.service.tracking.trace.TrackingStepLogger;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于颜色号和钢卷状态的通用连续生产线剪切算法。
 */
@Component
public class ShearTrackingAlgorithmImpl implements TrackingAlgorithm<ShearResult> {
    /** 非点位来源的首刀或首次分切长度。 */
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** 读取 shear/status 配置与运行态，并在持久化成功后保存 shear runtime。 */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    /** 输出触发、上下文、判型、刀次、长度、写库和 runtime 提交步骤日志。 */
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
        if (input.getPreviousSnapshot() == null) {
            trackingStepLogger.log(input, "剪切触发沿检查", TrackingStepLogger.details(
                    "previousAvailable", false,
                    "triggered", false));
            return results;
        }
        StatusTrackingContext context = input.getStatusContext();
        if (context == null) {
            trackingStepLogger.log(input, "剪切事件跳过", TrackingStepLogger.details(
                    "reason", "statusContext 不存在"));
            return results;
        }
        ShearTrackingSection tracking = configOptional.get().getTracking();
        // 计算阶段只修改运行态副本；数据库成功前不会覆盖 Redis 展示态。
        ShearTrackingRuntime workingRuntime = runtime(input.getUnitCode());
        calculatePoints(input, tracking, context, workingRuntime,
                tracking.getUncoilerShearPoint(), true, results);
        calculatePoints(input, tracking, context, workingRuntime,
                tracking.getCoilerShearPoint(), false, results);
        return results;
    }

    @Override
    public void afterPersist(TrackingInput input, List<ShearResult> results) {
        if (input == null || results == null || results.isEmpty()
                || !trackingProperties.shearStorageEnabled()) {
            return;
        }
        ShearTrackingRuntime runtime = runtime(input.getUnitCode());
        trackingStepLogger.log(input, "剪切记录写入", TrackingStepLogger.details(
                "resultCount", results.size()));
        for (ShearResult result : results) {
            applyResult(runtime, result);
        }
        runtime.setUpdatedAt(Instant.now());
        runtimeRepositoryDispatcher.saveRuntime(runtime);
        trackingStepLogger.log(input, "剪切运行态提交", TrackingStepLogger.details(
                "resultCount", results.size(),
                "pointCount", runtime.getPoints().size()));
    }

    /**
     * 按配置顺序检查一侧的全部剪刀，仅接受“正常位 -> 非正常位”的离开沿。
     * 单点异常在此隔离，不阻断同一 MQTT 帧中其他剪刀的计算。
     */
    private void calculatePoints(TrackingInput input,
                                 ShearTrackingSection tracking,
                                 StatusTrackingContext context,
                                 ShearTrackingRuntime runtime,
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
                                "porTrCodes", deviceCodes(point),
                                "previous", previous,
                                "latest", latest,
                                "normalPos", point.getNormalPos(),
                                "triggered", triggered));
                if (!triggered) {
                    continue;
                }
                ShearResult result = calculatePoint(
                        input, tracking, context, runtime, point, uncoilerSide);
                if (result != null) {
                    results.add(result);
                }
            } catch (RuntimeException e) {
                trackingStepLogger.log(input, "剪切事件跳过", point == null ? null : point.getName(),
                        TrackingStepLogger.details(
                                "reason", e.getMessage(),
                                "porTrCodes", point == null ? null : deviceCodes(point)));
            }
        }
    }

    /**
     * 对一个有效触发沿完成设备解析、连续线判型、物料归属、刀次和长度计算。
     * 返回值只携带待持久化结果和待提交 runtime 数据，不在本方法写数据库或 Redis。
     */
    private ShearResult calculatePoint(TrackingInput input,
                                       ShearTrackingSection tracking,
                                       StatusTrackingContext context,
                                       ShearTrackingRuntime runtime,
                                       ShearPointConfig point,
                                       boolean uncoilerSide) {
        DeviceSide associatedSide = uncoilerSide ? DeviceSide.UNCOILER : DeviceSide.COILER;
        AssociatedEndpoint associated = associatedEndpoint(context, point, associatedSide);
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
        if (kind == null) {
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
            kind = uncoilerSide
                    ? classifyUncoiler(por, tr, shearColor, tracking.getTailExperience())
                    : classifyCoiler(input, tracking, runtime, point, por, tr, shearColor);
        }
        Endpoint material = material(kind, uncoilerSide, por, tr);
        CounterDecision decision = counter(runtime, point.getName(), material, kind,
                por.remainingLength, tracking.getShearExperience(), true);
        LengthDecision lengthDecision = shearLength(
                input.getLatestSnapshot(), tracking, point, kind, uncoilerSide, decision);
        Integer typeCode = typeCode(point, kind);
        trackingStepLogger.log(input, "连续线剪切类型判定", point.getName(),
                TrackingStepLogger.details(
                        "porTrCodes", deviceCodes(point),
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
                .shearKind(kind)
                .inMatNo(material.coilNo)
                .inMatNoProdNo(material.productNo)
                .shearType(typeCode)
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
    private ShearKind classifyUncoiler(Endpoint por,
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
    private ShearKind classifyCoiler(TrackingInput input,
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
        CounterDecision nextTail = counter(runtime, point.getName(), tr, ShearKind.TAIL,
                por.remainingLength, tracking.getShearExperience(), false);
        return nextTail.counter.getCutNo() <= tailLimit ? ShearKind.TAIL : ShearKind.HEAD;
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
     * 计算本刀长度及 setNumber；分切长度来自相邻剩余长度差，其他类型按入口或飞剪配置读取。
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
        if (uncoilerSide) {
            if (decision.counter.getCutNo() == 1) {
                return new LengthDecision(ZERO, null);
            }
            CutSetting cutSetting = kind == ShearKind.HEAD ? settings.getHead() : settings.getTail();
            return new LengthDecision(
                    requiredDecimal(snapshot, tracking, cutSetting.getLength()),
                    integerValue(snapshot, tracking, cutSetting.getNumber()));
        }
        if (kind == ShearKind.TAIL && decision.counter.getCutNo() == 1) {
            return new LengthDecision(ZERO, null);
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
     * apply=true 写入的仍是 calculate 阶段副本，持久化成功后才由 afterPersist 正式提交。
     */
    private CounterDecision counter(ShearTrackingRuntime runtime,
                                    String pointCode,
                                    Endpoint material,
                                    ShearKind kind,
                                    BigDecimal porRemainLength,
                                    BigDecimal experience,
                                    boolean apply) {
        ShearPointRuntime pointRuntime = runtime.getPoints().computeIfAbsent(
                pointCode, key -> ShearPointRuntime.builder().build());
        String materialKey = materialKey(material.coilNo, material.productNo);
        ShearMaterialRuntime materialRuntime = pointRuntime.getMaterials().computeIfAbsent(
                materialKey, key -> ShearMaterialRuntime.builder()
                        .coilNo(material.coilNo)
                        .productNo(material.productNo)
                        .build());
        ShearCounterRuntime previous = materialRuntime.getCounters().get(kind);
        BigDecimal previousRemain = previous == null ? null : previous.getLastPorRemainLength();
        BigDecimal delta = previousRemain == null ? null : previousRemain.subtract(porRemainLength);
        ShearCounterRuntime next;
        if (previous == null) {
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
            materialRuntime.getCounters().put(kind, next);
        }
        return new CounterDecision(next, previousRemain, delta);
    }

    /** 将一条已持久化结果还原成计数状态，并合并到待保存的正式 runtime。 */
    private void applyResult(ShearTrackingRuntime runtime, ShearResult result) {
        ShearPointRuntime point = runtime.getPoints().computeIfAbsent(
                result.getShearPointCode(), key -> ShearPointRuntime.builder().build());
        String materialKey = materialKey(result.getInMatNo(), result.getInMatNoProdNo());
        ShearMaterialRuntime material = point.getMaterials().computeIfAbsent(
                materialKey, key -> ShearMaterialRuntime.builder()
                        .coilNo(result.getInMatNo())
                        .productNo(result.getInMatNoProdNo())
                        .build());
        material.getCounters().put(result.getShearKind(), ShearCounterRuntime.builder()
                .shearNo(result.getShearNo())
                .cutNo(result.getCutNo())
                .lastPorRemainLength(result.getPorRemainLength())
                .build());
    }

    /** 获取当前机组 runtime 的深拷贝；服务重启且无内存态时从空计数开始。 */
    private ShearTrackingRuntime runtime(String unitCode) {
        return runtimeRepositoryDispatcher.findRuntimeAs(
                unitCode, TrackingType.SHEAR, ShearTrackingRuntime.class)
                .map(this::copyRuntime)
                .orElseGet(() -> ShearTrackingRuntime.builder()
                        .unitCode(unitCode)
                        .trackingType(TrackingType.SHEAR)
                        .points(new LinkedHashMap<>())
                        .build());
    }

    private ShearTrackingRuntime copyRuntime(ShearTrackingRuntime source) {
        Map<String, ShearPointRuntime> points = new LinkedHashMap<>();
        if (source.getPoints() != null) {
            source.getPoints().forEach((pointCode, pointRuntime) -> {
                Map<String, ShearMaterialRuntime> materials = new LinkedHashMap<>();
                if (pointRuntime != null && pointRuntime.getMaterials() != null) {
                    pointRuntime.getMaterials().forEach((materialKey, material) -> {
                        Map<ShearKind, ShearCounterRuntime> counters = new LinkedHashMap<>();
                        if (material != null && material.getCounters() != null) {
                            material.getCounters().forEach((kind, counter) -> counters.put(kind,
                                    ShearCounterRuntime.builder()
                                            .shearNo(counter.getShearNo())
                                            .cutNo(counter.getCutNo())
                                            .lastPorRemainLength(counter.getLastPorRemainLength())
                                            .build()));
                        }
                        materials.put(materialKey, ShearMaterialRuntime.builder()
                                .coilNo(material.getCoilNo())
                                .productNo(material.getProductNo())
                                .counters(counters)
                                .build());
                    });
                }
                points.put(pointCode, ShearPointRuntime.builder().materials(materials).build());
            });
        }
        return ShearTrackingRuntime.builder()
                .unitCode(source.getUnitCode())
                .trackingType(TrackingType.SHEAR)
                .updatedAt(source.getUpdatedAt())
                .points(points)
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
            throw new IllegalArgumentException("por_tr_codes 为空");
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
        if (point != null && point.getPorTrCodes() != null && !point.getPorTrCodes().isEmpty()) {
            return point.getPorTrCodes();
        }
        return point == null || point.getPorTrCode() == null
                ? Collections.emptyList() : Collections.singletonList(point.getPorTrCode());
    }

    private BigDecimal last(List<BigDecimal> values) {
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer typeCode(ShearPointConfig point, ShearKind kind) {
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
                "porTrCodes", deviceCodes(point)));
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

    private String materialKey(String coilNo, String productNo) {
        return coilNo.length() + ":" + coilNo + ":" + productNo;
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
        /** 长度对应的设定数量；非点位计算长度为 null。 */
        private final Integer setNumber;

        private LengthDecision(BigDecimal length, Integer setNumber) {
            this.length = length;
            this.setNumber = setNumber;
        }
    }
}
