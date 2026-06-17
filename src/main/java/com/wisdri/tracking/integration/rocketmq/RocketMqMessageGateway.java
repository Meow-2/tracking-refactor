package com.wisdri.tracking.integration.rocketmq;

/**
 * RocketMQ 消息发送网关。
 *
 * <p>用于隔离具体 RocketMQ 客户端 API，便于发布适配器测试和后续替换实现。</p>
 */
public interface RocketMqMessageGateway {
    /**
     * 发送 RocketMQ 文本消息。
     */
    void send(String payload);
}
