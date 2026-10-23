package com.wisdri.tracking.application.usecase.passHistoryTracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.status.*;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import com.wisdri.tracking.domain.repository.product.RepeatProdNoRepository;
import com.wisdri.tracking.domain.service.point.PointReader;
import com.wisdri.tracking.domain.service.tracking.CellCodeResolver;
import com.wisdri.tracking.domain.service.tracking.status.StatusCoilNoNormalizer;
import com.wisdri.tracking.domain.service.tracking.status.StatusDeviceResolver;
import com.wisdri.tracking.infrastructure.dto.history.*;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.history.*;
import com.wisdri.tracking.infrastructure.service.feign.gateway.CubeApiGateway;
import com.wisdri.tracking.infrastructure.service.feign.gateway.QualityServiceGateway;
import com.wisdri.tracking.infrastructure.service.feign.gateway.timeseries.history.TimeSeriesHistoryGateway;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.*;

/** 从历史点位补全道次质量数据，不修改点位、生产次数或实时缓存。 */
@Component
public class PassHistoryTrackingUseCase {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss").appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final Duration MAX_AGE = Duration.ofSeconds(5);

    @Resource
    private CubeApiGateway cubeApiGateway;
    @Resource
    private TimeSeriesHistoryGateway historyGateway;
    @Resource
    private RepeatProdNoRepository repeatProdNoRepository;
    @Resource
    private QualityServiceGateway qualityServiceGateway;

    /** 先完成历史扫描，再按切换顺序同步写入；单道次失败保留数据并继续。 */
    public List<PassOutputResult> execute(String unitCode, LocalDateTime startTime, LocalDateTime endTime) {
        List<PassOutputResult> results = prepare(unitCode, startTime, endTime);
        for (PassOutputResult result : results) {
            if (!"READY".equals(result.getStatus())) {
                continue;
            }
            try {
                qualityServiceGateway.write(result.getOutput());
                result.setStatus("SUCCESS");
            } catch (RuntimeException e) {
                result.setStatus("FAILED");
                result.setReason(e.getMessage());
            }
        }
        return results;
    }

    /** 仅生成数据，不写质量接口；每次固化配置，切换时间决定事件是否属于请求范围。 */
    public List<PassOutputResult> prepare(String unitCode, LocalDateTime startTime, LocalDateTime endTime) {
        TrackingHistoryMetadata metadata = cubeApiGateway.fetchHistoryMetadata(unitCode, TrackingType.STATUS);
        StatusTrackingSection tracking = ((StatusTrackingConfig) metadata.getTrackingConfig()).getTracking();
        requireQualityConfig(tracking);
        List<State> states = queryStates(metadata, tracking, startTime.minus(Duration.ofHours(1)), endTime);
        List<PassOutputResult> results = new ArrayList<>();
        for (int i = 1; i < states.size(); i++) {
            State previous = states.get(i - 1);
            State next = states.get(i);
            Integer oldPass = passNo(previous.getValue());
            Integer newPass = passNo(next.getValue());
            // 跳号只结算实际观测到的旧道次；回退、归零、非法值不形成事件。
            if (oldPass == null || newPass == null || newPass <= oldPass) {
                continue;
            }
            LocalDateTime switchTime = parse(next.getFirstTime());
            if (!switchTime.isBefore(startTime) && switchTime.isBefore(endTime)) {
                results.add(preparePass(unitCode, metadata, tracking, previous, oldPass, switchTime));
            }
        }
        return results;
    }

    /** 单道次数据异常跳过，外部查询失败标记失败；不影响后续道次。 */
    private PassOutputResult preparePass(String unitCode, TrackingHistoryMetadata metadata,
                                         StatusTrackingSection tracking, State state, int passNo, LocalDateTime switchTime) {
        PassOutputResult result = new PassOutputResult();
        result.setPassNo(passNo);
        result.setSwitchTime(switchTime);
        result.setSourceTimes(new LinkedHashMap<>());
        try {
            result.setOutput(buildOutput(unitCode, metadata, tracking, state, passNo, result.getSourceTimes()));
            result.setStatus("READY");
        } catch (IllegalArgumentException e) {
            result.setStatus("SKIPPED");
            result.setReason(e.getMessage());
        } catch (RuntimeException e) {
            result.setStatus("FAILED");
            result.setReason(e.getMessage());
        }
        return result;
    }

    private RollingPassOutput buildOutput(String unitCode, TrackingHistoryMetadata metadata,
                                           StatusTrackingSection tracking, State state, int passNo,
                                           Map<String, LocalDateTime> sourceTimes) {
        Bounds bounds = queryBounds(metadata, tracking, state);
        if (bounds.getFirstTime() == null || bounds.getLastTime() == null) {
            throw new IllegalArgumentException("道次内没有速度达标帧");
        }
        LocalDateTime first = parse(bounds.getFirstTime());
        LocalDateTime last = parse(bounds.getLastTime());
        if (first.isAfter(last) || first.isBefore(parse(state.getFirstTime()))
                || last.isAfter(parse(state.getLastTime()))) {
            throw new IllegalArgumentException("首末帧超出道次区间");
        }
        // 首末时间只由速度确定；字段缺失不移动时间，首末入口卷不一致时整道次跳过。
        Map<DeviceSide, StatusPointGroup> firstDevices = selectDevices(metadata, tracking, passNo, first, sourceTimes);
        Map<DeviceSide, StatusPointGroup> lastDevices = first.equals(last) ? firstDevices
                : selectDevices(metadata, tracking, passNo, last, sourceTimes);
        StatusPointGroup firstSource = firstDevices.get(DeviceSide.UNCOILER);
        StatusPointGroup lastSource = lastDevices.get(DeviceSide.UNCOILER);
        StatusPointGroup lastTarget = lastDevices.get(DeviceSide.COILER);
        Map<String, JsonNode> firstValues = latest(metadata, tracking, first, sourceTimes, firstSource.getCoilNo());
        Map<String, JsonNode> lastValues = latest(metadata, tracking, last, sourceTimes,
                lastSource.getCoilNo(), lastTarget.getRemainingLength(), tracking.getRolling().getOutThicknessPoint());
        String coil = coilNo(lastValues.get(path(tracking, lastSource.getCoilNo())));
        if (!coil.equals(coilNo(firstValues.get(path(tracking, firstSource.getCoilNo()))))) {
            throw new IllegalArgumentException("首末帧入口钢卷号不一致");
        }
        BigDecimal length = positive(lastValues.get(path(tracking, lastTarget.getRemainingLength())), "卷取长度");
        BigDecimal thickness = positive(lastValues.get(path(tracking, tracking.getRolling().getOutThicknessPoint())), "出口厚度");
        // 历史流程统一使用上海本地时间，仅在复用实时质量模型时转换为绝对时间。
        return RollingPassOutput.builder().unitCode(unitCode).inMatNo(coil)
                .inMatRepeatProdNo(repeatProdNoRepository.findLatestBefore(unitCode, coil, first))
                .cellCode(CellCodeResolver.resolve(unitCode, null, null, passNo, null))
                .startAt(first.atZone(ZONE).toInstant()).endAt(last.atZone(ZONE).toInstant()).outMatLength(length).outMatThick(thickness).build();
    }

    private List<State> queryStates(TrackingHistoryMetadata metadata, StatusTrackingSection tracking,
                                    LocalDateTime start, LocalDateTime end) {
        PointHistoryMetadata point = point(metadata, tracking, tracking.getRolling().getPassNoPoint());
        StatesRequest request = new StatesRequest();
        request.setDatabase(point.getDatabase());
        request.setTable(point.getObjectName());
        request.setField(point.getCode());
        request.setStartTime(format(start));
        request.setEndTime(format(end));
        return historyGateway.queryStates(request);
    }

    /** 使用旧道次自身闭区间，保留最后一帧和单帧道次。 */
    private Bounds queryBounds(TrackingHistoryMetadata metadata, StatusTrackingSection tracking, State state) {
        PointHistoryMetadata point = point(metadata, tracking, tracking.getStartCondition().getPoint());
        BoundsRequest request = new BoundsRequest();
        request.setDatabase(point.getDatabase());
        request.setTable(point.getObjectName());
        request.setField(point.getCode());
        request.setStartTime(state.getFirstTime());
        request.setEndTime(state.getLastTime());
        request.setMin(tracking.getRolling().getQualityMinSpeed());
        return historyGateway.queryBounds(request);
    }

    private Map<DeviceSide, StatusPointGroup> selectDevices(TrackingHistoryMetadata metadata, StatusTrackingSection tracking,
                                                           int passNo, LocalDateTime at, Map<String, LocalDateTime> sourceTimes) {
        PointConfig directionPoint = tracking.getRolling().getDirectPoint();
        JsonNode raw = latest(metadata, tracking, at, sourceTimes, directionPoint).get(path(tracking, directionPoint));
        boolean direction = direction(raw);
        Map<DeviceSide, StatusPointGroup> selected = new EnumMap<>(DeviceSide.class);
        for (StatusPointGroup group : tracking.getPoints()) {
            DeviceSide side = StatusDeviceResolver.side(group, passNo, direction,
                    Boolean.TRUE.equals(tracking.getRolling().getDirectReverse()));
            if (side != null && selected.put(side, group) != null) {
                throw new IllegalArgumentException("无法唯一确定" + side.getDesc() + "设备");
            }
        }
        if (selected.size() != 2) {
            throw new IllegalArgumentException("缺少完整开卷、卷取设备对");
        }
        return selected;
    }

    /** 按点位身份匹配跨库、跨表结果，保留来源时间并限制最大滞后为 5 秒。 */
    private Map<String, JsonNode> latest(TrackingHistoryMetadata metadata, StatusTrackingSection tracking,
                                        LocalDateTime at, Map<String, LocalDateTime> sourceTimes, PointConfig... configs) {
        Map<String, PointHistoryMetadata> points = new LinkedHashMap<>();
        for (PointConfig config : configs) {
            PointHistoryMetadata point = point(metadata, tracking, config);
            points.put(path(tracking, config), point);
        }
        Map<String, Point> queries = new LinkedHashMap<>();
        for (PointHistoryMetadata point : points.values()) {
            Point query = new Point();
            query.setDatabase(point.getDatabase());
            query.setTable(point.getObjectName());
            query.setField(point.getCode());
            queries.put(key(query), query);
        }
        LatestRequest request = new LatestRequest();
        request.setAtTime(format(at));
        request.setPoints(new ArrayList<>(queries.values()));
        Map<String, Latest> rows = new HashMap<>();
        for (Latest row : historyGateway.queryLatest(request)) {
            rows.put(key(row), row);
        }
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (Map.Entry<String, PointHistoryMetadata> entry : points.entrySet()) {
            PointHistoryMetadata point = entry.getValue();
            Latest row = rows.get(point.getDatabase() + "." + point.getObjectName() + "." + point.getCode());
            if (row == null || !row.isFound() || row.getSourceTime() == null || row.getValue() == null || row.getValue().isNull()) {
                throw new IllegalArgumentException("点位缺失：" + entry.getKey());
            }
            LocalDateTime source = parse(row.getSourceTime());
            sourceTimes.put(format(at) + "/" + entry.getKey(), source);
            if (source.isAfter(at) || Duration.between(source, at).compareTo(MAX_AGE) > 0) {
                throw new IllegalArgumentException("点位时间无效或滞后超过 5 秒：" + entry.getKey());
            }
            values.put(entry.getKey(), row.getValue());
        }
        return values;
    }

    private void requireQualityConfig(StatusTrackingSection tracking) {
        if (tracking == null || tracking.getRolling() == null
                || !Boolean.TRUE.equals(tracking.getRolling().getQualityOutputEnabled())
                || tracking.getRolling().getQualityMinSpeed() == null || tracking.getStartCondition() == null
                || tracking.getPoints() == null || tracking.getPoints().isEmpty()) {
            throw new IllegalArgumentException("status 缺少道次质量配置或未启用质量输出");
        }
    }

    private PointHistoryMetadata point(TrackingHistoryMetadata metadata, StatusTrackingSection tracking, PointConfig config) {
        PointHistoryMetadata point = metadata.getPointMetadata().get(path(tracking, config));
        if (point == null) {
            throw new IllegalArgumentException("历史点位映射缺失：" + path(tracking, config));
        }
        return point;
    }

    private String path(StatusTrackingSection tracking, PointConfig config) {
        if (config == null || config.getName() == null) {
            throw new IllegalArgumentException("点位配置缺失");
        }
        return PointReader.pathResolve(tracking.getPointPrefix(), config.getName());
    }

    private Integer passNo(JsonNode value) {
        try {
            if (value == null || !value.isNumber()) {
                return null;
            }
            int pass = value.decimalValue().intValueExact();
            return pass > 0 && pass <= 999 ? pass : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private boolean direction(JsonNode value) {
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNumber() && (value.decimalValue().compareTo(BigDecimal.ZERO) == 0
                || value.decimalValue().compareTo(BigDecimal.ONE) == 0)) {
            return value.decimalValue().signum() != 0;
        }
        throw new IllegalArgumentException("轧制方向值无效");
    }

    /** 清洗历史入口卷号，并将倒数第三位的 3 转为 2，统一用于首末校验、生产次数查询和质量输出。 */
    private String coilNo(JsonNode value) {
        String coil = value != null && value.isTextual() ? StatusCoilNoNormalizer.normalize(value.textValue()) : null;
        if (coil == null) {
            throw new IllegalArgumentException("入口钢卷号无效");
        }
        // 仅转换指定位置，短卷号及其他位置的字符保持原样。
        int index = coil.length() - 3;
        if (index >= 0 && coil.charAt(index) == '3') {
            return coil.substring(0, index) + '2' + coil.substring(index + 1);
        }
        return coil;
    }

    private BigDecimal positive(JsonNode value, String name) {
        if (value == null || !value.isNumber() || value.decimalValue().signum() <= 0) {
            throw new IllegalArgumentException(name + "必须是正数");
        }
        return value.decimalValue();
    }

    private String key(Point point) {
        return point.getDatabase() + "." + point.getTable() + "." + point.getField();
    }

    private LocalDateTime parse(String time) {
        return LocalDateTime.parse(time, TIME);
    }

    private String format(LocalDateTime time) {
        return TIME.format(time);
    }
}
