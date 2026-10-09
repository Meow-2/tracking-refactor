package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.service.feign.client.TimeSeriesStorageFeignClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/** 时序数据存储服务访问网关，沿用三次尝试与指数退避。 */
@Component
public class TimeSeriesStorageGateway {
    @Resource
    private TimeSeriesStorageFeignClient timeSeriesStorageFeignClient;

    @Resource
    private ExternalServiceRetryExecutor retryExecutor;

    /** 创建或更新时序表。 */
    public void createTable(TimeSeriesTableRequest request) {
        retryExecutor.invoke("时序数据存储服务", "create-table",
                () -> timeSeriesStorageFeignClient.createTable(request), R::isSuccess);
    }

    /** 列式写入时序数据。 */
    public void saveColumn(String table, TimeSeriesDataRequest request) {
        retryExecutor.invoke("时序数据存储服务", "save-column",
                () -> timeSeriesStorageFeignClient.saveColumn(table, request), R::isSuccess);
    }
}
