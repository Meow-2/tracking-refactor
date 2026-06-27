package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.process.PointConfig;
import com.wisdri.tracking.domain.model.config.process.PointDataType;
import com.wisdri.tracking.domain.model.config.process.SegmentConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.*;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.springframework.beans.factory.annotation.Value;
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
    private static final List<TimeSeriesTableRule> NON_TAG_COLUMN = Arrays.asList(
            tableRule("coil_no", TimeSeriesDataType.STRING.getCode(), false),
            tableRule("head_length", TimeSeriesDataType.FLOAT.getCode(), false),
            tableRule("speed", TimeSeriesDataType.FLOAT.getCode(), false),
            tableRule("pass_no", TimeSeriesDataType.INT.getCode(), false)
    );

    @Value("${time-series-storage.database}")
    private String database;

    /**
     * 时序数据存储服务网关。
     */
    @Resource
    private TimeSeriesStorageGateway timeSeriesStorageGateway;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.PROCESS == trackingType;
    }

    @Override
    public void createTable(ProcessTrackingConfig config) {
        if (config == null || config.getSegments() == null || config.getSegments().isEmpty()) {
            return;
        }

        for (SegmentConfig segment : config.getSegments()) {
            TimeSeriesTableRequest request = new TimeSeriesTableRequest();
            request.setMode(TimeSeriesTableMode.COLUMN.getCode());
            request.setBucket(database);
            request.setMeasurement(tableName(config.getUnitCode(), config.getTrackingType(), segment.getCode()));
            List<TimeSeriesTableRule> rules = new ArrayList<>(NON_TAG_COLUMN);
            if (segment.getPoints() != null) {
                for (PointConfig point : segment.getPoints()) {
                    rules.add(tableRule(point.getName(), timeSeriesDataType(point.getType()), true));
                }
            }
            request.setRule(rules);
            timeSeriesStorageGateway.createTable(request);
        }
    }

    @Override
    public void save(List<ProcessResult> results) {
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
        add(values, "coil_no", result.getCoilNo(), false, timestamp);
        add(values, "head_length", result.getHeadLength(), false, timestamp);
        add(values, "speed", result.getSpeed(), false, timestamp);
        add(values, "pass_no", result.getPassNo(), false, timestamp);
        if (result.getParameters() != null) {
            for (Map.Entry<String, Object> entry : result.getParameters().entrySet()) {
                add(values, entry.getKey(), entry.getValue(), true, timestamp);
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

    private String timeSeriesDataType(PointDataType pointDataType) {
        if (pointDataType == PointDataType.STRING) {
            return TimeSeriesDataType.STRING.getCode();
        }
        if (pointDataType == PointDataType.INT || pointDataType == PointDataType.BOOL) {
            return TimeSeriesDataType.INT.getCode();
        }
        return TimeSeriesDataType.FLOAT.getCode();
    }

}
