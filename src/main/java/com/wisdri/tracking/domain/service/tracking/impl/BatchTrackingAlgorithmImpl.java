package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.config.batch.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.batch.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
import com.wisdri.tracking.domain.model.runtime.batch.BatchSegmentRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.batch.BatchResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import com.wisdri.tracking.domain.service.steplog.TrackingStepLogger;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 批次跟踪算法实现。
 * <p>
 * 一份模板配置由 MQTT 订阅展开为多个模板实例，本算法每次只处理其中一个实例，
 * 并为 north、south 两侧分别生成批次结果。
 */
@Component
@Slf4j
public class BatchTrackingAlgorithmImpl implements TrackingAlgorithm<BatchResult> {
    private static final String TEMPLATE_PLACEHOLDER = "{template}";
    private static final String COMMON_SEGMENT = "common";
    private static final List<String> RESULT_SEGMENTS = Arrays.asList("north", "south");

    /**
     * 跟踪配置和运行态仓储分发器。
     */
    @Resource
    private TrackingRuntimeRepositoryDispatcher runtimeRepositoryDispatcher;

    /**
     * 点位事件处理分发器。
     */
    @Resource
    private PointEventHandlerDispatcher pointEventHandlerDispatcher;

    @Resource
    private TrackingStepLogger trackingStepLogger;

    /** 按机组和卷号逐次分配生产序号，不依赖 status 运行态。 */
    @Resource
    private RepeatProdNoRepository repeatProdNoRepository;

    /**
     * 支持批次跟踪。
     */
    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.BATCH == trackingType;
    }

    /**
     * 计算当前模板实例的南北两侧批次结果。
     */
    @Override
    public List<BatchResult> calculate(TrackingInput input) {
        long startedAt = System.nanoTime();
        validateInput(input);
        Optional<BatchTrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), input.getTrackingType(), BatchTrackingConfig.class);
        trackingStepLogger.log(input, "计算开始", TrackingStepLogger.details(
                "configPresent", configOptional.isPresent()
        ));
        if (!configOptional.isPresent()) {
            return complete(input, startedAt, "缺少跟踪配置",
                    null, new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
        }

        BatchTrackingConfig config = configOptional.get();
        pointEventHandlerDispatcher.handle(input, config);
        PointSnapshot latest = input.getLatestSnapshot();
        BigDecimal productionStatus = productionStatus(latest, config.getTracking(), input.getTemplateCode());
        Map<String, String> coilNos = coilNos(latest, config.getTracking(), input.getTemplateCode());
        BatchTrackingRuntime previous = runtimeRepositoryDispatcher.findRuntimeAs(
                input.getUnitCode(), TrackingType.BATCH, input.getTemplateCode(), BatchTrackingRuntime.class)
                .orElse(null);
        Map<String, BatchSegmentRuntime> segmentStates = resolveSegments(input, config, coilNos, previous);
        StartCondition startCondition = config.getTracking() == null
                ? null : config.getTracking().getStartCondition();
        boolean productionReached = productionReached(productionStatus, config.getTracking());
        trackingStepLogger.log(input, "生产条件检查", TrackingStepLogger.details(
                "point", pointName(startCondition == null ? null : startCondition.getPoint()),
                "actual", productionStatus,
                "threshold", startCondition == null ? null : startCondition.getThreshold(),
                "passed", productionReached
        ));
        trackingStepLogger.log(input, "钢卷号解析", TrackingStepLogger.details(
                "north", coilNos.get("north"),
                "south", coilNos.get("south")
        ));
        List<BatchResult> results = new ArrayList<>();
        if (productionReached) {
            Instant generatedAt = Instant.now();
            for (String segmentCode : RESULT_SEGMENTS) {
                String coilNo = coilNos.get(segmentCode);
                if (coilNo == null) {
                    trackingStepLogger.log(input, "区段跳过", segmentCode,
                            TrackingStepLogger.details("reason", "钢卷号为空"));
                    continue;
                }
                SegmentConfig segment = segment(config, segmentCode);
                if (segment == null) {
                    trackingStepLogger.log(input, "区段跳过", segmentCode,
                            TrackingStepLogger.details("reason", "缺少区段配置"));
                    continue;
                }
                Map<String, Object> parameters = parameters(
                        latest, config, segment, input.getTemplateCode());
                BatchResult result = BatchResult.builder()
                        .unitCode(config.getUnitCode())
                        .trackingType(config.getTrackingType())
                        .templateCode(input.getTemplateCode())
                        .segmentCode(segment.getCode())
                        .segmentName(segment.getName())
                        .coilNo(coilNo)
                        .repeatProdNo(segmentStates.get(segmentCode).getRepeatProdNo())
                        .productionStatus(productionStatus)
                        .parameters(parameters)
                        .generatedAt(generatedAt)
                        .receivedAt(latest == null ? null : latest.getReceivedAt())
                        .build();
                results.add(result);
                trackingStepLogger.log(input, "区段结果生成", segmentCode,
                        TrackingStepLogger.details(
                                "coilNo", coilNo,
                                "repeatProdNo", result.getRepeatProdNo(),
                                "productionStatus", productionStatus,
                                "parameters", parameters
                        ));
            }
        }
        return complete(input, startedAt, productionReached ? null : "未达到生产条件",
                productionStatus, coilNos, segmentStates, results);
    }

    /** 每侧按自身的卷身份判断上卷；短暂缺值保留身份，但缺值帧不生成结果。 */
    private Map<String, BatchSegmentRuntime> resolveSegments(TrackingInput input,
                                                               BatchTrackingConfig config,
                                                               Map<String, String> coilNos,
                                                               BatchTrackingRuntime previous) {
        Map<String, BatchSegmentRuntime> states = new LinkedHashMap<>();
        Integer configuredThreshold = config.getTracking() == null
                ? null : config.getTracking().getCurrentClearThreshold();
        int clearThreshold = configuredThreshold == null ? 1 : configuredThreshold;
        for (String segmentCode : RESULT_SEGMENTS) {
            BatchSegmentRuntime old = previousSegment(previous, segmentCode);
            String coilNo = coilNos.get(segmentCode);
            if (coilNo == null) {
                int missing = old == null || old.getNullCount() == null ? 1
                        : old.getNullCount() == Integer.MAX_VALUE ? Integer.MAX_VALUE : old.getNullCount() + 1;
                states.put(segmentCode, old != null && old.getCoilNo() != null
                        && missing <= clearThreshold
                        ? BatchSegmentRuntime.builder().coilNo(old.getCoilNo())
                                .repeatProdNo(old.getRepeatProdNo()).nullCount(missing)
                                .allocationPending(old.getAllocationPending()).build()
                        : BatchSegmentRuntime.builder().nullCount(missing).build());
                continue;
            }

            boolean sameCoil = old != null && coilNo.equals(old.getCoilNo());
            boolean allocate = !sameCoil || Boolean.TRUE.equals(old.getAllocationPending());
            Integer repeatProdNo = sameCoil ? old.getRepeatProdNo() : null;
            boolean pending = allocate;
            if (repeatProdNo == null) {
                try {
                    repeatProdNo = allocate
                            ? repeatProdNoRepository.allocateNext(input.getUnitCode(), coilNo)
                            : repeatProdNoRepository.findLatestOrAllocate(input.getUnitCode(), coilNo);
                    pending = false;
                    trackingStepLogger.log(input, allocate ? "重复生产次数分配" : "重复生产次数补查或补写",
                            segmentCode, TrackingStepLogger.details("coilNo", coilNo,
                                    "repeatProdNo", repeatProdNo));
                } catch (RuntimeException e) {
                    log.warn("BAF重复生产次数处理失败，机组={}，模板={}，工艺侧={}，卷号={}",
                            input.getUnitCode(), input.getTemplateCode(), segmentCode, coilNo, e);
                    trackingStepLogger.log(input, "重复生产次数失败", segmentCode,
                            TrackingStepLogger.details("coilNo", coilNo));
                }
            }
            states.put(segmentCode, BatchSegmentRuntime.builder().coilNo(coilNo)
                    .repeatProdNo(repeatProdNo).nullCount(0).allocationPending(pending).build());
        }
        return states;
    }

    /** 旧版运行态只有 coilNos；升级时保留卷身份，缺少 PG 记录则补写首次序号。 */
    private BatchSegmentRuntime previousSegment(BatchTrackingRuntime previous, String segmentCode) {
        if (previous == null) {
            return null;
        }
        BatchSegmentRuntime state = previous.getSegments() == null
                ? null : previous.getSegments().get(segmentCode);
        if (state != null) {
            return state;
        }
        String coilNo = previous.getCoilNos() == null ? null : previous.getCoilNos().get(segmentCode);
        return coilNo == null ? null : BatchSegmentRuntime.builder().coilNo(coilNo).nullCount(0).build();
    }

    private List<BatchResult> complete(TrackingInput input,
                                       long startedAt,
                                       String reason,
                                       BigDecimal productionStatus,
                                       Map<String, String> coilNos,
                                       Map<String, BatchSegmentRuntime> segmentStates,
                                       List<BatchResult> results) {
        trackingStepLogger.log(input, "计算完成", TrackingStepLogger.details(
                "resultCount", results.size(),
                "reason", reason,
                "elapsedMillis", (System.nanoTime() - startedAt) / 1_000_000L
        ));
        return saveRuntime(input, productionStatus, coilNos, segmentStates, results);
    }

    /**
     * 保存本帧运行态。卷号是否变化不影响结果生成，也不会触发配置刷新。
     */
    private List<BatchResult> saveRuntime(TrackingInput input,
                                          BigDecimal productionStatus,
                                          Map<String, String> coilNos,
                                          Map<String, BatchSegmentRuntime> segmentStates,
                                          List<BatchResult> results) {
        runtimeRepositoryDispatcher.saveRuntime(BatchTrackingRuntime.builder()
                .unitCode(input.getUnitCode())
                .trackingType(input.getTrackingType())
                .templateCode(input.getTemplateCode())
                .productionStatus(productionStatus)
                .coilNos(new LinkedHashMap<>(coilNos))
                .segments(new LinkedHashMap<>(segmentStates))
                .updatedAt(Instant.now())
                .build());
        return results;
    }

    /**
     * 读取生产状态；缺失或格式错误统一视为无效状态。
     */
    private BigDecimal productionStatus(PointSnapshot latest,
                                        TrackingSection tracking,
                                        String templateCode) {
        StartCondition condition = tracking == null ? null : tracking.getStartCondition();
        if (condition == null) {
            return null;
        }
        try {
            return PointReader.decimalValue(latest,
                    trackingPointPath(tracking, condition.getPoint(), templateCode));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断当前生产状态是否达到配置阈值。
     */
    private boolean productionReached(BigDecimal productionStatus, TrackingSection tracking) {
        StartCondition condition = tracking == null ? null : tracking.getStartCondition();
        if (productionStatus == null || condition == null || condition.getThreshold() == null) {
            return false;
        }
        return productionStatus.compareTo(condition.getThreshold()) >= 0;
    }

    /**
     * 按 tracking.point 中的 segment 绑定并清洗南北卷号。
     */
    private Map<String, String> coilNos(PointSnapshot latest,
                                        TrackingSection tracking,
                                        String templateCode) {
        Map<String, String> coilNos = new LinkedHashMap<>();
        if (tracking == null || tracking.getPoints() == null) {
            return coilNos;
        }
        for (TrackingPointGroup group : tracking.getPoints()) {
            String segmentCode = resultSegmentCode(group == null ? null : group.getSegment());
            if (segmentCode == null) {
                continue;
            }
            String coilNo = trimInvisible(PointReader.stringValue(latest,
                    trackingPointPath(tracking, group.getCoilNo(), templateCode)));
            if (coilNo == null || coilNo.isEmpty()) {
                coilNos.remove(segmentCode);
            } else {
                coilNos.put(segmentCode, coilNo);
            }
        }
        return coilNos;
    }

    /**
     * common 参数先写入，当前侧参数后写入并覆盖同名字段；空值不进入结果。
     */
    private Map<String, Object> parameters(PointSnapshot latest,
                                           BatchTrackingConfig config,
                                           SegmentConfig segment,
                                           String templateCode) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        putParameters(parameters, latest, segment(config, COMMON_SEGMENT), templateCode);
        putParameters(parameters, latest, segment, templateCode);
        return parameters;
    }

    private void putParameters(Map<String, Object> parameters,
                               PointSnapshot latest,
                               SegmentConfig segment,
                               String templateCode) {
        if (segment == null || segment.getPoints() == null) {
            return;
        }
        for (PointConfig point : segment.getPoints()) {
            String pointName = pointName(point);
            Object value = PointReader.rawValue(latest, segmentPointPath(segment, pointName, templateCode));
            if (value != null && pointName != null) {
                parameters.put(pointName, value);
            }
        }
    }

    private SegmentConfig segment(BatchTrackingConfig config, String segmentCode) {
        if (config.getSegments() == null) {
            return null;
        }
        for (SegmentConfig segment : config.getSegments()) {
            if (segment != null && sameCode(segmentCode, segment.getCode())) {
                return segment;
            }
        }
        return null;
    }

    private String trackingPointPath(TrackingSection tracking,
                                     PointConfig point,
                                     String templateCode) {
        return PointReader.pathResolve(resolveTemplate(tracking == null ? null : tracking.getPointPrefix(), templateCode),
                pointName(point));
    }

    private String segmentPointPath(SegmentConfig segment,
                                    String pointName,
                                    String templateCode) {
        return PointReader.pathResolve(resolveTemplate(segment == null ? null : segment.getPointPrefix(), templateCode),
                pointName);
    }

    private String resolveTemplate(String value, String templateCode) {
        return value == null ? null : value.replace(TEMPLATE_PLACEHOLDER, templateCode);
    }

    private String resultSegmentCode(String segmentCode) {
        for (String expected : RESULT_SEGMENTS) {
            if (sameCode(expected, segmentCode)) {
                return expected;
            }
        }
        return null;
    }

    private boolean sameCode(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String pointName(PointConfig point) {
        return point == null ? null : point.getName();
    }

    /**
     * 清理卷号首尾的空白、控制字符和零宽格式字符。
     */
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

    private void validateInput(TrackingInput input) {
        if (input == null || input.getTrackingType() != TrackingType.BATCH) {
            throw new IllegalArgumentException("批次跟踪输入和跟踪类型不能为空");
        }
        if (input.getTemplateCode() == null || input.getTemplateCode().trim().isEmpty()) {
            throw new IllegalArgumentException("批次跟踪 templateCode 不能为空");
        }
    }
}
