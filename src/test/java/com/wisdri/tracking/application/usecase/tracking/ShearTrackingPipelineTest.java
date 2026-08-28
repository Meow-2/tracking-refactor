package com.wisdri.tracking.application.usecase.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.domain.repository.tracking.TrackingResultRepositoryDispatcher;
import com.wisdri.tracking.domain.service.tracking.TrackingAlgorithmDispatcher;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShearTrackingPipelineTest {
    @Test
    void commitsRuntimeOnlyAfterResultsAreSaved() {
        TrackingAlgorithmDispatcher algorithmDispatcher = mock(TrackingAlgorithmDispatcher.class);
        TrackingResultRepositoryDispatcher resultRepositoryDispatcher =
                mock(TrackingResultRepositoryDispatcher.class);
        ShearResult result = ShearResult.builder().trackingType(TrackingType.SHEAR).build();
        when(algorithmDispatcher.calculate(any(TrackingInput.class)))
                .thenReturn(Arrays.asList(result));
        TrackingTaskConsumerUseCase useCase = new TrackingTaskConsumerUseCase();
        ReflectionTestUtils.setField(useCase, "trackingAlgorithmDispatcher", algorithmDispatcher);
        ReflectionTestUtils.setField(useCase, "trackingResultRepositoryDispatcher", resultRepositoryDispatcher);

        useCase.consume(TrackingInput.builder()
                .unitCode("LINE-X")
                .trackingType(TrackingType.SHEAR)
                .build());

        InOrder order = inOrder(algorithmDispatcher, resultRepositoryDispatcher);
        order.verify(algorithmDispatcher).calculate(any(TrackingInput.class));
        order.verify(resultRepositoryDispatcher).save(anyList());
        order.verify(algorithmDispatcher).afterPersist(any(TrackingInput.class), anyList());
    }
}
