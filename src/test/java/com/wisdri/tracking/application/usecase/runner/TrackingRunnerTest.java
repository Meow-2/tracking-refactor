package com.wisdri.tracking.application.usecase.runner;

import com.wisdri.tracking.application.usecase.tracking.PointSubscriptionUseCase;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TrackingRunnerTest {
    @Test
    void externalUnitSkipsTrackingInitializationIgnoringCase() {
        for (String unit : new String[]{"external", "EXTERNAL"}) {
            PointSubscriptionUseCase subscriptions = mock(PointSubscriptionUseCase.class);
            runner(unit, subscriptions).run(new DefaultApplicationArguments(new String[0]));
            verifyNoInteractions(subscriptions);
        }
    }

    @Test
    void actualUnitsStillInitializeTracking() {
        for (String unit : new String[]{"cp1", "zrm1"}) {
            PointSubscriptionUseCase subscriptions = mock(PointSubscriptionUseCase.class);
            runner(unit, subscriptions).run(new DefaultApplicationArguments(new String[0]));
            verify(subscriptions).start();
        }
    }

    @Test
    void missingUnitRetainsOriginalInitializationBehavior() {
        PointSubscriptionUseCase subscriptions = mock(PointSubscriptionUseCase.class);
        runner(null, subscriptions).run(new DefaultApplicationArguments(new String[0]));
        verify(subscriptions).start();
    }

    private TrackingRunner runner(String unit, PointSubscriptionUseCase subscriptions) {
        TrackingProperties properties = new TrackingProperties();
        properties.setUnit(unit);
        TrackingRunner runner = new TrackingRunner();
        ReflectionTestUtils.setField(runner, "trackingProperties", properties);
        ReflectionTestUtils.setField(runner, "pointSubscriptionUseCase", subscriptions);
        return runner;
    }
}
