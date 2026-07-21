package com.wisdri.tracking.infrastructure.dto.feign.timeseries;

import com.wisdri.tracking.domain.model.config.PointDataType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeSeriesDataTypeTest {

    @Test
    void containsAllQualityTsModelTypesWithoutMixing() {
        List<String> codes = Arrays.stream(TimeSeriesDataType.values())
                .map(TimeSeriesDataType::getCode)
                .collect(Collectors.toList());

        assertEquals(Arrays.asList(
                "boolean", "char", "byte", "short", "int",
                "long", "float", "double", "string", "date"), codes);
    }

    @Test
    void mapsEveryPointTypeToItsIndependentTimeSeriesType() {
        assertEquals(Arrays.asList("boolean", "short", "float", "string"),
                Arrays.stream(PointDataType.values())
                        .map(PointDataType::getCode)
                        .collect(Collectors.toList()));
        for (PointDataType pointDataType : PointDataType.values()) {
            TimeSeriesDataType timeSeriesDataType = TimeSeriesDataType.fromPointDataType(pointDataType);
            assertEquals(pointDataType.name(), timeSeriesDataType.name());
            assertEquals(pointDataType.getCode(), timeSeriesDataType.getCode());
        }
        assertThrows(NullPointerException.class,
                () -> TimeSeriesDataType.fromPointDataType(null));
    }
}
