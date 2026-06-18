package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import com.wisdri.tracking.common.response.R;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesDataRequest;
import com.wisdri.tracking.infrastructure.dto.feign.timeseries.TimeSeriesTableRequest;
import com.wisdri.tracking.infrastructure.service.feign.client.TimeSeriesStorageFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.function.Supplier;

/**
 * 时序数据存储服务访问网关。
 */
@Slf4j
@Component
public class TimeSeriesStorageGateway {
    /**
     * 最大尝试次数。
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * 初始退避时间，单位毫秒。
     */
    private static final long INITIAL_BACKOFF_MILLIS = 200L;

    /**
     * 最大退避时间，单位毫秒。
     */
    private static final long MAX_BACKOFF_MILLIS = 2000L;

    /**
     * 时序数据存储 Feign 客户端。
     */
    @Resource
    private TimeSeriesStorageFeignClient timeSeriesStorageFeignClient;

    /**
     * 创建或更新时序表。
     */
    public void createTable(TimeSeriesTableRequest request) {
        invokeWithRetry("create-table", () -> timeSeriesStorageFeignClient.createTable(request));
    }

    /**
     * 列式写入时序数据。
     */
    public void saveColumn(String database, String table, TimeSeriesDataRequest request) {
        invokeWithRetry("save-column", () -> timeSeriesStorageFeignClient.saveColumn(database, table, request));
    }

    /**
     * 带指数退避的调用。
     */
    private void invokeWithRetry(String action, Supplier<R<Boolean>> call) {
        RuntimeException lastException = null;
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                R<Boolean> response = call.get();
                if (R.isSuccess(response)) {
                    return;
                }
                lastException = new ExternalServiceException("时序数据存储服务返回失败");
            } catch (RuntimeException e) {
                lastException = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(action, attempt, backoffMillis, lastException);
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
            }
        }
        throw new ExternalServiceException("时序数据存储服务调用失败: " + action, lastException);
    }

    /**
     * 执行退避等待。
     */
    private void sleep(String action, int attempt, long backoffMillis, RuntimeException exception) {
        log.warn("时序数据存储服务调用失败，准备重试，action={}, attempt={}, backoffMillis={}",
                action, attempt, backoffMillis, exception);
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("时序数据存储服务重试等待被中断: " + action, e);
        }
    }

}
