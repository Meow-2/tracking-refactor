package com.wisdri.tracking.application.usecase.runner;

import com.wisdri.tracking.application.usecase.tracking.PointSubscriptionUseCase;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 跟踪应用启动编排入口。
 */
@Component
public class TrackingRunner implements ApplicationRunner {
    /**
     * 点位消息订阅用例。
     */
    @Resource
    private PointSubscriptionUseCase pointSubscriptionUseCase;

    @Resource
    private TrackingProperties trackingProperties;

    /**
     * 应用启动后触发跟踪初始化。这里不直接处理 MQTT 或 RocketMQ 细节，
     * 只把启动事件交给应用用例，便于后续替换启动方式。
     */
    @Override
    public void run(ApplicationArguments args) {
        // 对外实例保留公共客户端，不执行机组配置刷新和跟踪订阅初始化。
        if ("external".equalsIgnoreCase(trackingProperties.getUnit())) {
            return;
        }
        pointSubscriptionUseCase.start();
    }
}
