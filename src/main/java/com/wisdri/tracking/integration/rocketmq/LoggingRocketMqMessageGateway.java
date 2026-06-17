package com.wisdri.tracking.integration.rocketmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志型 RocketMQ 发送网关。
 *
 * <p>真实 RocketMQ producer 接入前，先以日志方式保留发送边界。</p>
 */
@Slf4j
@Component
public class LoggingRocketMqMessageGateway implements RocketMqMessageGateway {
    /**
     * 以日志方式模拟发送 RocketMQ 消息。
     */
    @Override
    public void send(String payload) {
        log.info("发送 RocketMQ 跟踪任务消息: {}", payload);
    }
}
