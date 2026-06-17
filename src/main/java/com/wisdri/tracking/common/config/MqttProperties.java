package com.wisdri.tracking.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * mqtt.* 配置绑定。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {
    /**
     * MQTT broker 地址。
     */
    private String host;

    /**
     * MQTT 用户名。
     */
    private String username;

    /**
     * MQTT 密码。
     */
    private String password;

    /**
     * MQTT 客户端 ID。
     */
    private String clientId;

    /**
     * 操作完成超时时间，单位毫秒。
     */
    private Integer completionTimeout;

    /**
     * MQTT QoS 等级。
     */
    private Integer qos;

    /**
     * 是否使用 clean session。
     */
    private Boolean cleanSession;

    /**
     * 连接超时时间，单位秒。
     */
    private Integer connectionTimeout;

    /**
     * 心跳间隔，单位秒。
     */
    private Integer keepAliveInterval;
}
