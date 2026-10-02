package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossSegmentConfig;
import com.wisdri.tracking.domain.model.config.ironloss.IronLossTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.ironloss.IronLossResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepository;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataType;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataValue;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableMode;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRule;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.properties.feign.TimeSeriesStorageProperties;
import com.wisdri.tracking.infrastructure.service.feign.gateway.TimeSeriesStorageGateway;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 铁损结果列式时序仓储；表名规则与 PROCESS 相同，类型码使用 ironloss。 */
@Repository
public class IronLossTrackingResultRepositoryImpl
        implements TrackingResultRepository<IronLossTrackingConfig, IronLossResult> {
    private static final List<TimeSeriesTableRule> FIXED_COLUMNS = Arrays.asList(
            rule("coil_no", TimeSeriesDataType.STRING.getCode(), true),
            rule("cell_code", TimeSeriesDataType.STRING.getCode(), true),
            rule("in_mat_prod_no", TimeSeriesDataType.INT.getCode(), true),
            rule("head_length", TimeSeriesDataType.FLOAT.getCode(), false)
    );

    @Resource
    private TrackingProperties trackingProperties;

    @Resource
    private TimeSeriesStorageProperties timeSeriesStorageProperties;

    @Resource
    private TimeSeriesStorageGateway timeSeriesStorageGateway;

    @Override
    public boolean support(TrackingType trackingType) {
        return TrackingType.IRONLOSS == trackingType;
    }

    /** 每个工艺段建一张表，动态列类型来自 Cube 点位元数据。 */
    @Override
    public void createTable(IronLossTrackingConfig config) {
        if (!storageEnabled() || config == null || config.getSegments() == null) {
            return;
        }
        for (IronLossSegmentConfig segment : config.getSegments()) {
            List<TimeSeriesTableRule> rules = new ArrayList<>(FIXED_COLUMNS);
            if (segment.getPoints() != null) {
                for (PointConfig point : segment.getPoints()) {
                    if (!"cell_code".equalsIgnoreCase(point.getName())) {
                        rules.add(rule(point.getName(),
                                TimeSeriesDataType.fromPointDataType(point.getType()).getCode(), false));
                    }
                }
            }
            TimeSeriesTableRequest request = new TimeSeriesTableRequest();
            request.setMode(TimeSeriesTableMode.COLUMN.getCode());
            request.setBucket(timeSeriesStorageProperties.getDatabase());
            request.setMeasurement(tableName(config.getUnitCode(), segment.getCode()));
            request.setRule(rules);
            timeSeriesStorageGateway.createTable(request);
        }
    }

    /** 接收时间作为行时间戳，固定字段与参数按列写入。 */
    @Override
    public void save(List<IronLossResult> results) {
        if (!storageEnabled() || results == null || results.isEmpty()) {
            return;
        }
        for (IronLossResult result : results) {
            Long timestamp = timestamp(result.getReceivedAt());
            List<TimeSeriesDataValue> values = new ArrayList<>();
            add(values, "coil_no", result.getCoilNo(), true, timestamp);
            add(values, "cell_code", result.getCellCode(), true, timestamp);
            add(values, "in_mat_prod_no", result.getInMatNoProdNo(), true, timestamp);
            add(values, "head_length", result.getHeadLength(), false, timestamp);
            if (result.getParameters() != null) {
                for (Map.Entry<String, Object> entry : result.getParameters().entrySet()) {
                    if (!"cell_code".equalsIgnoreCase(entry.getKey())) {
                        add(values, entry.getKey(), entry.getValue(), false, timestamp);
                    }
                }
            }
            TimeSeriesDataRequest request = new TimeSeriesDataRequest();
            request.setTimestamp(timestamp);
            request.setIsSameDeviceTime(true);
            request.setValues(values);
            timeSeriesStorageGateway.saveColumn(
                    tableName(result.getUnitCode(), result.getSegmentCode()), request);
        }
    }

    private boolean storageEnabled() {
        return trackingProperties == null || trackingProperties.ironlossStorageEnabled();
    }

    private String tableName(String unitCode, String segmentCode) {
        return unitCode + "_" + TrackingType.IRONLOSS.getCode() + "_" + segmentCode;
    }

    private Long timestamp(Instant receivedAt) {
        return receivedAt == null ? null : receivedAt.toEpochMilli();
    }

    private void add(List<TimeSeriesDataValue> values, String name,
                     Object value, boolean tag, Long timestamp) {
        values.add(new TimeSeriesDataValue(name, value, true, timestamp, tag));
    }

    private static TimeSeriesTableRule rule(String name, String type, boolean tag) {
        return new TimeSeriesTableRule(name, type, tag);
    }
}
