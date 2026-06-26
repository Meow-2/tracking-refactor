package com.wisdri.tracking.infrastructure.properties;

import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskConsumer;
import lombok.Data;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.support.DefaultListenerContainer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Annotation;
import java.time.Duration;

/**
 * rocketmq.* 配置绑定与 RocketMQ 消费容器装配。
 */
@Data
@Configuration
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
        private Integer requestTimeout = 3;

        /**
         * 最大重试次数。
         */
        private Integer maxAttempts = 3;

        /**
         * 是否启用 SSL。
         */
        private Boolean sslEnabled = false;
    }

    @Data
    public static class PushConsumer {
        /**
         * 是否启用 push consumer。
         */
        private Boolean enabled = false;

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
        private String tag = "*";

        /**
         * 消费组。
         */
        private String consumerGroup;

        /**
         * 请求超时时间，单位秒。
         */
        private Integer requestTimeout = 3;

        /**
         * 是否启用 SSL。
         */
        private Boolean sslEnabled = false;
    }

    /**
     * 根据 rocketmq.push-consumer.* 配置创建 RocketMQ push consumer 容器。
     */
    @Bean
    public DefaultListenerContainer trackingTaskConsumerContainer(TrackingTaskConsumer trackingTaskConsumer) {
        DefaultListenerContainer container = new DefaultListenerContainer();
        container.setName("trackingTaskConsumerContainer");
        container.setRocketMQMessageListener(messageListenerMetadata());
        container.setMessageListener(trackingTaskConsumer);
        container.setEndpoints(pushConsumer.getEndpoints());
        container.setTopic(pushConsumer.getTopic());
        container.setTag(pushConsumer.getTag());
        container.setConsumerGroup(pushConsumer.getConsumerGroup());
        container.setRequestTimeout(Duration.ofSeconds(pushConsumer.getRequestTimeout()));
        container.setSslEnabled(pushConsumer.getSslEnabled());
        container.setType("tag");
        return container;
    }

    private RocketMQMessageListener messageListenerMetadata() {
        return new RocketMQMessageListener() {
            @Override
            public String accessKey() {
                return "";
            }

            @Override
            public String secretKey() {
                return "";
            }

            @Override
            public String endpoints() {
                return "";
            }

            @Override
            public String topic() {
                return "";
            }

            @Override
            public String tag() {
                return "*";
            }

            @Override
            public String filterExpressionType() {
                return "tag";
            }

            @Override
            public String consumerGroup() {
                return "";
            }

            @Override
            public int requestTimeout() {
                return 3;
            }

            @Override
            public int maxCachedMessageCount() {
                return 1024;
            }

            @Override
            public int maxCacheMessageSizeInBytes() {
                return 67108864;
            }

            @Override
            public int consumptionThreadCount() {
                return 20;
            }

            @Override
            public String namespace() {
                return "";
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RocketMQMessageListener.class;
            }
        };
    }

}
