package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.*;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 过程跟踪结果仓储。
 */
@Repository
public class ProcessTrackingResultRepositoryImpl implements TrackingResultRepository<ProcessTrackingConfig, ProcessResult> {
    private static final List<TimeSeriesTableRule> FIXED_COLUMNS = Arrays.asList(
            tableRule("coil_no", TimeSeriesDataType.STRING.getCode(), true),
            tableRule("in_mat_prod_no", TimeSeriesDataType.INT.getCode(), true),
            tableRule("head_length", TimeSeriesDataType.FLOAT.getCode(), false),
            tableRule("speed", TimeSeriesDataType.FLOAT.getCode(), false),
            tableRule("pass_no", TimeSeriesDataType.INT.getCode(), true)
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
        return TrackingType.PROCESS == trackingType;
    }

    @Override
    public void createTable(ProcessTrackingConfig config) {
        if (trackingProperties != null && !trackingProperties.processStorageEnabled()) {
            return;
        }
        if (config == null || config.getSegments() == null || config.getSegments().isEmpty()) {
            return;
        }

        for (SegmentConfig segment : config.getSegments()) {
            TimeSeriesTableRequest request = new TimeSeriesTableRequest();
            request.setMode(TimeSeriesTableMode.COLUMN.getCode());
            request.setBucket(timeSeriesStorageProperties.getDatabase());
            request.setMeasurement(tableName(config.getUnitCode(), config.getTrackingType(), segment.getCode()));
            List<TimeSeriesTableRule> rules = new ArrayList<>(FIXED_COLUMNS);
            if (segment.getPoints() != null) {
                for (PointConfig point : segment.getPoints()) {
                    rules.add(tableRule(point.getName(),
                            TimeSeriesDataType.fromPointDataType(point.getType()).getCode(), false));
                }
            }
            request.setRule(rules);
            timeSeriesStorageGateway.createTable(request);
        }
    }

    @Override
    public void save(List<ProcessResult> results) {
        if (trackingProperties != null && !trackingProperties.processStorageEnabled()) {
            return;
        }
        if (results == null || results.isEmpty()) {
            return;
        }
        for (ProcessResult result : results) {
            timeSeriesStorageGateway.saveColumn(
                    tableName(result.getUnitCode(), result.getTrackingType(), result.getSegmentCode()),
                    processRequest(result)
            );
        }
    }

    private String tableName(String unitCode, TrackingType trackingType, String segmentCode) {
        return unitCode + "_" + (trackingType == null ? null : trackingType.getCode()) + "_" + segmentCode;
    }

    private TimeSeriesDataRequest processRequest(ProcessResult result) {
        Long timestamp = timestamp(result.getReceivedAt());
        TimeSeriesDataRequest request = new TimeSeriesDataRequest();
        request.setTimestamp(timestamp);
        request.setIsSameDeviceTime(true);
        request.setValues(processValues(result, timestamp));
        return request;
    }

    private List<TimeSeriesDataValue> processValues(ProcessResult result, Long timestamp) {
        List<TimeSeriesDataValue> values = new ArrayList<>();
        add(values, "coil_no", result.getCoilNo(), true, timestamp);
        add(values, "in_mat_prod_no", result.getInMatNoProdNo(), true, timestamp);
        add(values, "head_length", result.getHeadLength(), false, timestamp);
        add(values, "speed", result.getSpeed(), false, timestamp);
        add(values, "pass_no", result.getPassNo(), true, timestamp);
        if (result.getParameters() != null) {
            for (Map.Entry<String, Object> entry : result.getParameters().entrySet()) {
                add(values, entry.getKey(), entry.getValue(), false, timestamp);
            }
        }
        return values;
    }

    private void add(List<TimeSeriesDataValue> values, String id, Object value, boolean tag, Long timestamp) {
        values.add(new TimeSeriesDataValue(id, value, true, timestamp, tag));
    }

    private Long timestamp(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static TimeSeriesTableRule tableRule(String id, String datatype, boolean tag) {
        return new TimeSeriesTableRule(id, datatype, tag);
    }

}
