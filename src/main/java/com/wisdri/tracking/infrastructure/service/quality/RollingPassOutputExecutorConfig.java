package com.wisdri.tracking.infrastructure.service.quality;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 质量接口独立有界线程池；拒绝策略不得在 status 调用线程执行 HTTP。 */
@Configuration
public class RollingPassOutputExecutorConfig {
    @Bean("rollingPassOutputExecutor")
    public ThreadPoolTaskExecutor rollingPassOutputExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("rolling-pass-output-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
