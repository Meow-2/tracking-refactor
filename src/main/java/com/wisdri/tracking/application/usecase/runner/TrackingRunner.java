package com.wisdri.tracking.application.usecase.runner;

import com.wisdri.tracking.application.usecase.tracking.PointSubscriptionUseCase;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 跟踪应用启动编排入口。
 */
@Component
@ConditionalOnProperty(prefix = "tracking.subscription", name = "enabled", havingValue = "true")
public class TrackingRunner implements ApplicationRunner {
    /**
     * 点位消息订阅用例。
     */
    @Resource
    private PointSubscriptionUseCase pointSubscriptionUseCase;

    /**
     * 应用启动后触发跟踪初始化。这里不直接处理 MQTT 或 RocketMQ 细节，
     * 只把启动事件交给应用用例，便于后续替换启动方式。
     */
    @Override
    public void run(ApplicationArguments args) {
        pointSubscriptionUseCase.start();
    }
}
