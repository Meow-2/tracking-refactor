package com.wisdri.tracking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * rocketmq.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rocketmq")
public class RocketMqConfig {
    /**
     * RocketMQ producer 配置。
     */
    private Producer producer = new Producer();

    /**
     * RocketMQ push consumer 配置。
     */
    private PushConsumer pushConsumer = new PushConsumer();

    @Data
    public static class Producer {
        /**
         * producer 连接 endpoints。
         */
        private String endpoints;

        /**
         * 跟踪任务 topic。
         */
        private String topic;

        /**
         * 请求超时时间，单位秒。
         */
        private Integer requestTimeout;

        /**
         * 最大重试次数。
         */
        private Integer maxAttempts;

        /**
         * 是否启用 SSL。
         */
        private Boolean sslEnabled;
    }

    @Data
    public static class PushConsumer {
        /**
         * consumer 连接 endpoints。
         */
        private String endpoints;

        /**
         * 消费 topic。
         */
        private String topic;

        /**
         * 消费 tag。
         */
        private String tag;

        /**
         * 消费组。
         */
        private String consumerGroup;

        /**
         * 请求超时时间，单位秒。
         */
        private Integer requestTimeout;

        /**
         * 是否启用 SSL。
         */
        private Boolean sslEnabled;
    }
}
