package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataValue;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRule;
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
    private static final String DATABASE = "aygg_tracking";
    private static final String PROCESS_TABLE = "process_tracking_result";

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
    public void createTable(ProcessTrackingConfig config, PointSnapshot latestSnapshot) {
        TimeSeriesTableRequest request = new TimeSeriesTableRequest();
        request.setMode("column");
        request.setBucket(DATABASE);
        request.setMeasurement(PROCESS_TABLE);
        request.setRule(Arrays.asList(
                rule("unit_code", "string", true),
                rule("tracking_type", "string", true),
                rule("segment_name", "string", true),
                rule("coil_no", "string", true),
                rule("head_length", "double", false),
                rule("speed", "double", false),
                rule("pass_no", "int", false)
        ));
        timeSeriesStorageGateway.createTable(request);
    }

    @Override
    public void save(List<ProcessResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (ProcessResult result : results) {
            timeSeriesStorageGateway.saveColumn(DATABASE, PROCESS_TABLE, processRequest(result));
        }
    }

    private TimeSeriesDataRequest processRequest(ProcessResult result) {
        Long timestamp = timestamp(result.getGeneratedAt());
        TimeSeriesDataRequest request = new TimeSeriesDataRequest();
        request.setTimestamp(timestamp);
        request.setIsSameDeviceTime(true);
        request.setValues(processValues(result, timestamp));
        return request;
    }

    private List<TimeSeriesDataValue> processValues(ProcessResult result, Long timestamp) {
        List<TimeSeriesDataValue> values = new ArrayList<>();
        add(values, "unit_code", result.getUnitCode(), true, timestamp);
        add(values, "tracking_type", result.getTrackingType() == null ? null : result.getTrackingType().getCode(), true, timestamp);
        add(values, "segment_name", result.getSegmentName(), true, timestamp);
        add(values, "coil_no", result.getCoilNo(), true, timestamp);
        add(values, "head_length", result.getHeadLength(), false, timestamp);
        add(values, "speed", result.getSpeed(), false, timestamp);
        add(values, "pass_no", result.getPassNo(), false, timestamp);
        if (result.getParameters() != null) {
            for (Map.Entry<String, Object> entry : result.getParameters().entrySet()) {
                add(values, entry.getKey(), entry.getValue(), false, timestamp);
            }
        }
        return values;
    }

    private void add(List<TimeSeriesDataValue> values, String id, Object value, boolean tag, Long timestamp) {
        TimeSeriesDataValue dataValue = new TimeSeriesDataValue();
        dataValue.setId(id);
        dataValue.setV(value);
        dataValue.setQ(true);
        dataValue.setT(timestamp);
        dataValue.setIsTag(tag);
        values.add(dataValue);
    }

    private Long timestamp(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private TimeSeriesTableRule rule(String id, String datatype, boolean tag) {
        TimeSeriesTableRule rule = new TimeSeriesTableRule();
        rule.setId(id);
        rule.setDatatype(datatype);
        rule.setIsTag(tag);
        return rule;
    }
}
