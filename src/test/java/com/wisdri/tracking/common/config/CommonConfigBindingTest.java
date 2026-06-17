package com.wisdri.tracking.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommonConfigBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    TrackingProperties.class,
                    MqttProperties.class,
                    RocketMqConfig.class,
                    OpenFeignConfig.class,
                    ThreadPoolConfig.class
            )
            .withPropertyValues(
                    "tracking.unit=cp1",
                    "tracking.data-storage=true",
                    "mqtt.host=tcp://172.16.203.11:1883",
                    "mqtt.username=admin",
                    "mqtt.password=public",
                    "mqtt.client-id=tracking-service-client",
                    "mqtt.completion-timeout=3000",
                    "mqtt.qos=1",
                    "mqtt.clean-session=false",
                    "mqtt.connection-timeout=10",
                    "mqtt.keep-alive-interval=20",
                    "rocketmq.producer.topic=tracking-data-topic",
                    "rocketmq.producer.endpoints=172.16.203.11:8081",
                    "rocketmq.producer.request-timeout=3",
                    "rocketmq.producer.max-attempts=3",
                    "rocketmq.producer.ssl-enabled=false",
                    "rocketmq.push-consumer.topic=tracking-data-topic",
                    "rocketmq.push-consumer.tag=*",
                    "rocketmq.push-consumer.consumer-group=tracking-data-consumer-group",
                    "rocketmq.push-consumer.endpoints=172.16.203.11:8081",
                    "rocketmq.push-consumer.request-timeout=3",
                    "rocketmq.push-consumer.ssl-enabled=false",
                    "cube-api.base-url=http://172.16.203.12:30800",
                    "cube-api.tree-root=/aygg_tracking",
                    "cube-api.connect-timeout=3000",
                    "cube-api.read-timeout=5000",
                    "data-storage.base-url=http://127.0.0.1:8886",
                    "data-storage.connect-timeout=3000",
                    "data-storage.read-timeout=5000",
                    "thread-pool.core-size=4",
                    "thread-pool.max-size=8",
                    "thread-pool.queue-capacity=100"
            );

    @Test
    void bindsTrackingMqttRocketMqFeignAndThreadPoolProperties() {
        contextRunner.run(context -> {
            TrackingProperties tracking = context.getBean(TrackingProperties.class);
            MqttProperties mqtt = context.getBean(MqttProperties.class);
            RocketMqConfig rocketMq = context.getBean(RocketMqConfig.class);
            OpenFeignConfig openFeign = context.getBean(OpenFeignConfig.class);
            ThreadPoolConfig threadPool = context.getBean(ThreadPoolConfig.class);

            assertThat(tracking.getUnit()).isEqualTo("cp1");
            assertThat(tracking.getDataStorage()).isTrue();
            assertThat(mqtt.getHost()).isEqualTo("tcp://172.16.203.11:1883");
            assertThat(mqtt.getClientId()).isEqualTo("tracking-service-client");
            assertThat(mqtt.getQos()).isEqualTo(1);
            assertThat(mqtt.getCleanSession()).isFalse();
            assertThat(rocketMq.getProducer().getTopic()).isEqualTo("tracking-data-topic");
            assertThat(rocketMq.getProducer().getEndpoints()).isEqualTo("172.16.203.11:8081");
            assertThat(rocketMq.getPushConsumer().getConsumerGroup()).isEqualTo("tracking-data-consumer-group");
            assertThat(openFeign.getCubeApi().getBaseUrl()).isEqualTo("http://172.16.203.12:30800");
            assertThat(openFeign.getCubeApi().getTreeRoot()).isEqualTo("/aygg_tracking");
            assertThat(openFeign.getDataStorage().getBaseUrl()).isEqualTo("http://127.0.0.1:8886");
            assertThat(threadPool.getCoreSize()).isEqualTo(4);
            assertThat(threadPool.getMaxSize()).isEqualTo(8);
            assertThat(threadPool.getQueueCapacity()).isEqualTo(100);
        });
    }
}
