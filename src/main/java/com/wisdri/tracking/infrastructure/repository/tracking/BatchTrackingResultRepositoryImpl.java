package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.batch.BatchTrackingConfig;
import com.wisdri.tracking.domain.model.config.batch.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.batch.BatchResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataType;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataValue;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableMode;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRule;
import com.wisdri.tracking.infrastructure.properties.TimeSeriesStorageProperties;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 批次跟踪结果时序仓储。
 * <p>
 * 同一机组的所有模板实例和南北侧共用一张表，通过 fb_code、segment_code 两个字段区分。
 */
@Repository
public class BatchTrackingResultRepositoryImpl
        implements TrackingResultRepository<BatchTrackingConfig, BatchResult> {
    private static final String NORTH_SEGMENT = "north";
    private static final String SOUTH_SEGMENT = "south";
    private static final List<String> DYNAMIC_SEGMENT_ORDER = Arrays.asList("common", "north", "south");
    private static final List<TimeSeriesTableRule> FIXED_COLUMNS = Arrays.asList(
            tableRule("fb_code", TimeSeriesDataType.INT.getCode(), false),
            tableRule("segment_code", TimeSeriesDataType.INT.getCode(), false),
            tableRule("coil_no", TimeSeriesDataType.STRING.getCode(), false),
            tableRule("head_length", TimeSeriesDataType.FLOAT.getCode(), false),
            tableRule("speed", TimeSeriesDataType.FLOAT.getCode(), false),
            tableRule("pass_no", TimeSeriesDataType.INT.getCode(), false),
            tableRule("prod_status", TimeSeriesDataType.FLOAT.getCode(), true)
    );

    /**
     * 时序存储服务配置。
     */
    @Resource
    private TimeSeriesStorageProperties timeSeriesStorageProperties;

    /**
     * 时序数据存储服务网关。
     */
    @Resource
    private TimeSeriesStorageGateway timeSeriesStorageGateway;

    /**
     * 跟踪应用配置。
     */
    @Resource
    private TrackingProperties trackingProperties;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.BATCH == trackingType;
    }

    /**
     * 创建或更新唯一批次结果表。
     */
    @Override
    public void createTable(BatchTrackingConfig config) {
        if (trackingProperties != null && !trackingProperties.trackingResultStorageEnabled()) {
            return;
        }
        if (config == null) {
            return;
        }
        TimeSeriesTableRequest request = new TimeSeriesTableRequest();
        request.setMode(TimeSeriesTableMode.COLUMN.getCode());
        request.setBucket(timeSeriesStorageProperties.getDatabase());
        request.setMeasurement(tableName(config.getUnitCode()));
        List<TimeSeriesTableRule> rules = new ArrayList<>(FIXED_COLUMNS);
        rules.addAll(dynamicRules(config));
        request.setRule(rules);
        timeSeriesStorageGateway.createTable(request);
    }

    /**
     * 将一帧产生的南北侧结果分别写入同一张表。
     */
    @Override
    public void save(List<BatchResult> results) {
        if (trackingProperties != null && !trackingProperties.trackingResultStorageEnabled()) {
            return;
        }
        if (results == null || results.isEmpty()) {
            return;
        }
        for (BatchResult result : results) {
            timeSeriesStorageGateway.saveColumn(tableName(result.getUnitCode()), batchRequest(result));
        }
    }

    /**
     * 汇总 common、north、south 的动态列，并校验同名列类型一致。
     */
    private List<TimeSeriesTableRule> dynamicRules(BatchTrackingConfig config) {
        Map<String, TimeSeriesTableRule> rules = new LinkedHashMap<>();
        for (String segmentCode : DYNAMIC_SEGMENT_ORDER) {
            SegmentConfig segment = segment(config, segmentCode);
            if (segment == null || segment.getPoints() == null) {
                continue;
            }
            for (PointConfig point : segment.getPoints()) {
                if (point == null || point.getName() == null || point.getName().trim().isEmpty()) {
                    continue;
                }
                TimeSeriesTableRule rule = tableRule(
                        point.getName(), TimeSeriesDataType.fromPointDataType(point.getType()).getCode(), true);
                TimeSeriesTableRule previous = rules.putIfAbsent(point.getName(), rule);
                if (previous != null && !previous.getDatatype().equals(rule.getDatatype())) {
                    throw new TrackingException("批次动态列类型冲突: " + point.getName());
                }
            }
        }
        return new ArrayList<>(rules.values());
    }

    private SegmentConfig segment(BatchTrackingConfig config, String code) {
        if (config.getSegments() == null) {
            return null;
        }
        for (SegmentConfig segment : config.getSegments()) {
            if (segment != null && segment.getCode() != null && segment.getCode().equalsIgnoreCase(code)) {
                return segment;
            }
        }
        return null;
    }

    private TimeSeriesDataRequest batchRequest(BatchResult result) {
        Long timestamp = timestamp(result);
        TimeSeriesDataRequest request = new TimeSeriesDataRequest();
        request.setTimestamp(timestamp);
        request.setIsSameDeviceTime(true);
        request.setValues(batchValues(result, timestamp));
        return request;
    }

    private List<TimeSeriesDataValue> batchValues(BatchResult result, Long timestamp) {
        List<TimeSeriesDataValue> values = new ArrayList<>();
        add(values, "fb_code", fbCode(result.getTemplateCode()), false, timestamp);
        add(values, "segment_code", segmentCode(result.getSegmentCode()), false, timestamp);
        add(values, "coil_no", result.getCoilNo(), false, timestamp);
        add(values, "prod_status", result.getProductionStatus(), false, timestamp);
        if (result.getParameters() != null) {
            for (Map.Entry<String, Object> entry : result.getParameters().entrySet()) {
                if (entry.getValue() != null) {
                    add(values, entry.getKey(), entry.getValue(), true, timestamp);
                }
            }
        }
        return values;
    }

    private Integer fbCode(String templateCode) {
        if (templateCode == null) {
            return null;
        }
        String digits = templateCode.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            throw new TrackingException("批次模板编码不包含数字: " + templateCode);
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            throw new TrackingException("批次模板编码数字部分超出整型范围: " + templateCode, e);
        }
    }

    private Integer segmentCode(String segmentCode) {
        if (SOUTH_SEGMENT.equalsIgnoreCase(segmentCode)) {
            return 0;
        }
        if (NORTH_SEGMENT.equalsIgnoreCase(segmentCode)) {
            return 1;
        }
        throw new TrackingException("不支持的批次工艺侧编码: " + segmentCode);
    }

    private void add(List<TimeSeriesDataValue> values,
                     String id,
                     Object value,
                     boolean tag,
                     Long timestamp) {
        values.add(new TimeSeriesDataValue(id, value, true, timestamp, tag));
    }

    /**
     * 表名统一转为小写，禁止按模板实例或工艺侧拆表。
     */
    private String tableName(String unitCode) {
        return unitCode.toLowerCase(Locale.ROOT) + "_batch";
    }

    /**
     * north 在原始 MQTT 接收时间上增加 1ms，避免与同帧 south 使用相同主时间戳而相互覆盖。
     */
    private Long timestamp(BatchResult result) {
        Instant receivedAt = result == null ? null : result.getReceivedAt();
        if (receivedAt == null) {
            return null;
        }
        long timestamp = receivedAt.toEpochMilli();
        return NORTH_SEGMENT.equalsIgnoreCase(result.getSegmentCode()) ? timestamp + 1 : timestamp;
    }

    private static TimeSeriesTableRule tableRule(String id, String datatype, boolean tag) {
        return new TimeSeriesTableRule(id, datatype, tag);
    }

}
