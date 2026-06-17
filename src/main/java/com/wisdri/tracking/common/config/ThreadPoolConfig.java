package com.wisdri.tracking.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * thread-pool.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "thread-pool")
public class ThreadPoolConfig {
    /**
     * 核心线程数。
     */
    private Integer coreSize = 4;

    /**
     * 最大线程数。
     */
    private Integer maxSize = 8;

    /**
     * 队列容量。
     */
    private Integer queueCapacity = 100;
}
