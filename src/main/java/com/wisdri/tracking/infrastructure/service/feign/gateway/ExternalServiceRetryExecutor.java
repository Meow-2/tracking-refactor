package com.wisdri.tracking.infrastructure.service.feign.gateway;

import com.wisdri.tracking.common.exception.ExternalServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** 外部接口统一的三次尝试与指数退避；调用方必须选择合适的执行线程。 */
@Slf4j
@Component
public class ExternalServiceRetryExecutor {
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 200L;
    private static final long MAX_BACKOFF_MILLIS = 2000L;

    /** 响应由调用方判断成功，失败时最多尝试三次并抛出最后一次异常。 */
    public <T> void invoke(String serviceName, String action, Supplier<T> call, Predicate<T> successful) {
        RuntimeException lastException = null;
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (successful.test(call.get())) {
                    return;
                }
                lastException = new ExternalServiceException(serviceName + "返回失败");
            } catch (RuntimeException e) {
                lastException = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                log.warn("{}调用失败，准备重试，action={}, attempt={}, backoffMillis={}",
                        serviceName, action, attempt, backoffMillis, lastException);
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ExternalServiceException(serviceName + "重试等待被中断: " + action, e);
                }
                backoffMillis = Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
            }
        }
        throw new ExternalServiceException(serviceName + "调用失败: " + action, lastException);
    }
}
