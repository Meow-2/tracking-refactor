package com.wisdri.tracking.infrastructure.service.quality;

import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import com.wisdri.tracking.domain.service.tracking.status.RollingPassOutputSubmitter;
import com.wisdri.tracking.infrastructure.service.feign.gateway.QualityServiceGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** 内存队列异步发送端；任务丢失或最终失败只记录日志，不反向阻塞 status。 */
@Slf4j
@Component
public class AsyncRollingPassOutputSubmitter implements RollingPassOutputSubmitter {
    private final ThreadPoolTaskExecutor executor;
    private final QualityServiceGateway gateway;

    public AsyncRollingPassOutputSubmitter(
            @Qualifier("rollingPassOutputExecutor") ThreadPoolTaskExecutor executor,
            QualityServiceGateway gateway) {
        this.executor = executor;
        this.gateway = gateway;
    }

    @Override
    public void submit(RollingPassOutput output) {
        try {
            executor.execute(() -> send(output));
        } catch (RuntimeException e) {
            log.error("换道质量接口任务入队失败，丢弃任务，机组={}，入口卷={}，生产次数={}，加工单元={}",
                    output.getUnitCode(), output.getInMatNo(), output.getInMatRepeatProdNo(),
                    output.getCellCode(), e);
        }
    }

    private void send(RollingPassOutput output) {
        try {
            gateway.write(output);
        } catch (RuntimeException e) {
            log.error("换道质量接口调用最终失败，机组={}，入口卷={}，生产次数={}，加工单元={}",
                    output.getUnitCode(), output.getInMatNo(), output.getInMatRepeatProdNo(),
                    output.getCellCode(), e);
        }
    }
}
