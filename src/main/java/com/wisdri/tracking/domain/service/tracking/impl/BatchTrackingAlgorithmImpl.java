package com.wisdri.tracking.domain.service.tracking.impl;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.config.batch.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.batch.TrackingSection;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.runtime.batch.BatchTrackingRuntime;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.batch.BatchResult;
import com.wisdri.tracking.domain.repository.runtime.TrackingRuntimeRepositoryDispatcher;
import com.wisdri.tracking.domain.service.point.PointEventHandlerDispatcher;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithm;
import org.springframework.stereotype.Component;

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
        validateInput(input);
        Optional<BatchTrackingConfig> configOptional = runtimeRepositoryDispatcher.findConfigAs(
                input.getUnitCode(), input.getTrackingType(), BatchTrackingConfig.class);
        if (!configOptional.isPresent()) {
            return saveRuntime(input, null, new LinkedHashMap<>(), new ArrayList<>());
        }

        BatchTrackingConfig config = configOptional.get();
        pointEventHandlerDispatcher.handle(input, config);
        PointSnapshot latest = input.getLatestSnapshot();
        BigDecimal productionStatus = productionStatus(latest, config.getTracking(), input.getTemplateCode());
        Map<String, String> coilNos = coilNos(latest, config.getTracking(), input.getTemplateCode());
        List<BatchResult> results = new ArrayList<>();
        if (productionReached(productionStatus, config.getTracking())) {
            Instant generatedAt = Instant.now();
            for (String segmentCode : RESULT_SEGMENTS) {
                String coilNo = coilNos.get(segmentCode);
                if (coilNo == null) {
                    continue;
                }
                SegmentConfig segment = segment(config, segmentCode);
                if (segment == null) {
                    continue;
                }
                results.add(BatchResult.builder()
                        .unitCode(config.getUnitCode())
                        .trackingType(config.getTrackingType())
                        .templateCode(input.getTemplateCode())
                        .segmentCode(segment.getCode())
                        .segmentName(segment.getName())
                        .coilNo(coilNo)
                        .productionStatus(productionStatus)
                        .parameters(parameters(latest, config, segment, input.getTemplateCode()))
                        .generatedAt(generatedAt)
                        .receivedAt(latest == null ? null : latest.getReceivedAt())
                        .build());
            }
        }
        return saveRuntime(input, productionStatus, coilNos, results);
    }

    /**
     * 保存本帧运行态。卷号是否变化不影响结果生成，也不会触发配置刷新。
     */
    private List<BatchResult> saveRuntime(TrackingInput input,
                                          BigDecimal productionStatus,
                                          Map<String, String> coilNos,
                                          List<BatchResult> results) {
        runtimeRepositoryDispatcher.saveRuntime(BatchTrackingRuntime.builder()
                .unitCode(input.getUnitCode())
                .trackingType(input.getTrackingType())
                .templateCode(input.getTemplateCode())
                .productionStatus(productionStatus)
                .coilNos(new LinkedHashMap<>(coilNos))
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
