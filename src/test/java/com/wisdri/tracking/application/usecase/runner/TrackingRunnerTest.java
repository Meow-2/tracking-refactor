package com.wisdri.tracking.application.usecase.runner;

import com.wisdri.tracking.application.usecase.tracking.PointSubscriptionUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TrackingRunnerTest {
    @Mock
    private PointSubscriptionUseCase pointSubscriptionUseCase;

    @Test
    void delegatesStartupToPointSubscriptionUseCase() {
        TrackingRunner runner = new TrackingRunner();
        ReflectionTestUtils.setField(runner, "pointSubscriptionUseCase", pointSubscriptionUseCase);

        runner.run(null);

        verify(pointSubscriptionUseCase).start();
    }
}
