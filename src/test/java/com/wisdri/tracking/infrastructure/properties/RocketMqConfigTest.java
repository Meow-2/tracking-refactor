package com.wisdri.tracking.infrastructure.properties;

import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskConsumer;
import com.wisdri.tracking.infrastructure.service.rocketmq.TrackingTaskProducer;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.DefaultListenerContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.LifecycleProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RocketMqConfigTest {
    @Test
    void externalUnitDisablesConsumerButKeepsProducerWithoutSendingMessages() {
        for (String unit : new String[]{"external", "EXTERNAL"}) {
            RocketMQClientTemplate template = mock(RocketMQClientTemplate.class);
            context(template).withPropertyValues("tracking.unit=" + unit,
                    "rocketmq.push-consumer.enabled=true").run(application -> {
                        assertThat(application).hasNotFailed().doesNotHaveBean(DefaultListenerContainer.class)
                                .hasSingleBean(TrackingTaskProducer.class)
                                .hasSingleBean(RocketMQClientTemplate.class);
                        verifyNoInteractions(template);
                    });
        }
    }

    @Test
    void actualUnitStillCreatesOriginalConsumerContainer() {
        RocketMQClientTemplate template = mock(RocketMQClientTemplate.class);
        context(template).withPropertyValues("tracking.unit=zrm1").run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(DefaultListenerContainer.class)
                    .hasSingleBean(TrackingTaskProducer.class);
            assertThat(application.getBean(DefaultListenerContainer.class).getTag()).isEqualTo("zrm1");
            verifyNoInteractions(template);
        });
    }

    private ApplicationContextRunner context(RocketMQClientTemplate template) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(RocketMqConfig.class, TrackingTaskProducer.class)
                .withInitializer(application -> {
                    application.getBeanFactory().registerSingleton("rocketMQClientTemplate", template);
                    application.getBeanFactory().registerSingleton("trackingTaskConsumer", mock(TrackingTaskConsumer.class));
                    // 校验容器装配，测试中不启动消费者或连接消息服务器。
                    application.getBeanFactory().registerSingleton("lifecycleProcessor", mock(LifecycleProcessor.class));
                })
                .withPropertyValues("rocketmq.push-consumer.endpoints=127.0.0.1:1",
                        "rocketmq.push-consumer.topic=tracking-test", "rocketmq.push-consumer.tag=zrm1",
                        "rocketmq.push-consumer.consumer-group=tracking-test-group");
    }
}
