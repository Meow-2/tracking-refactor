package com.wisdri.tracking.infrastructure.service.quality;

import com.wisdri.tracking.domain.model.tracking.status.RollingPassOutput;
import com.wisdri.tracking.infrastructure.service.feign.gateway.QualityServiceGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AsyncRollingPassOutputSubmitterTest {
    @Test
    void submitOnlyQueuesHttpWork() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        QualityServiceGateway gateway = mock(QualityServiceGateway.class);
        RollingPassOutput output = output();

        new AsyncRollingPassOutputSubmitter(executor, gateway).submit(output);

        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(queued.capture());
        verify(gateway, never()).write(any(RollingPassOutput.class));
        queued.getValue().run();
        verify(gateway).write(output);
    }

    @Test
    void rejectedQueueDoesNotPropagateToStatus() {
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        QualityServiceGateway gateway = mock(QualityServiceGateway.class);
        doThrow(new TaskRejectedException("full")).when(executor).execute(any(Runnable.class));

        assertThatCode(() -> new AsyncRollingPassOutputSubmitter(executor, gateway).submit(output()))
                .doesNotThrowAnyException();
        verify(gateway, never()).write(any(RollingPassOutput.class));
    }

    private RollingPassOutput output() {
        return RollingPassOutput.builder().unitCode("ZRM1").inMatNo("COIL-A")
                .inMatRepeatProdNo(1).cellCode("ZRM1001").build();
    }
}
