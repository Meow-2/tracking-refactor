package com.wisdri.tracking.infrastructure.properties;

import lombok.Data;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Component;

/**
 * mqtt.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {
    /**
     * MQTT broker 地址。
     */
    private String url;

    /**
     * MQTT client id。
     */
    private String clientId;

    /**
     * MQTT 用户名。
     */
    private String username;

    /**
     * MQTT 密码。
     */
    private String password;

    /**
     * 订阅 QoS。
     */
    private Integer qos = 1;

    /**
     * 操作完成超时时间，单位毫秒。
     */
    private Long completionTimeout = 3000L;

    /**
     * 是否清理会话。
     */
    private Boolean cleanSession = false;

    /**
     * 连接超时时间，单位秒。
     */
    private Integer connectionTimeout = 10;

    /**
     * 心跳间隔，单位秒。
     */
    private Integer keepAliveInterval = 20;

    /**
     * MQTT 消息输入通道。
     * <p>
     * 所有动态订阅收到的消息都会进入该通道，再由 MqttMessageListener 统一处理。
     */
    @Bean
    @ConditionalOnProperty(prefix = "mqtt", name = "url")
    public MessageChannel trackingMqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * MQTT Paho client factory。
     * <p>
     * 根据 mqtt.* 配置创建连接参数。只有配置 mqtt.url 时才装配，便于在未启用
     * MQTT 的测试或部署环境中跳过 MQTT 客户端初始化。
     */
    @Bean
    @ConditionalOnProperty(prefix = "mqtt", name = "url")
    public DefaultMqttPahoClientFactory trackingMqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{url});
        options.setCleanSession(Boolean.TRUE.equals(cleanSession));
        options.setConnectionTimeout(connectionTimeout);
        options.setKeepAliveInterval(keepAliveInterval);
        if (username != null && !username.trim().isEmpty()) {
            options.setUserName(username);
        }
        if (password != null) {
            options.setPassword(password.toCharArray());
        }
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * MQTT 动态订阅 adapter。
     * <p>
     * 初始化时不绑定固定 topic，启动后由 MqttSubscriptionRegistry 根据跟踪配置
     * 调用 addTopic 动态订阅。
     */
    @Bean
    @ConditionalOnProperty(prefix = "mqtt", name = "url")
    public MqttPahoMessageDrivenChannelAdapter trackingMqttAdapter(
            DefaultMqttPahoClientFactory trackingMqttClientFactory,
            MessageChannel trackingMqttInputChannel) {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId,
                trackingMqttClientFactory
        );
        adapter.setCompletionTimeout(completionTimeout);
        adapter.setOutputChannel(trackingMqttInputChannel);
        return adapter;
    }
}
