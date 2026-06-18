package com.wisdri.tracking.infrastructure.service.feign.client;

import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 时序数据存储服务 OpenFeign 客户端。
 */
@FeignClient(
        name = "time-series-storage",
        url = "${time-series-storage.base-url}"
)
public interface TimeSeriesStorageFeignClient {
    /**
     * 创建或更新时序表。
     */
    @PostMapping("/manage/table/create")
    R<Boolean> createTable(@RequestBody TimeSeriesTableRequest request);

    /**
     * 列式写入时序数据。
     */
    @PostMapping("/data/db/{database}/table/{table}/_column")
    R<Boolean> saveColumn(@PathVariable("database") String database,
                          @PathVariable("table") String table,
                          @RequestBody TimeSeriesDataRequest request);
}
