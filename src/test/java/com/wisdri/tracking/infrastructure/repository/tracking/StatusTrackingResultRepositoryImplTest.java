package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.status.StatusResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatCode;

class StatusTrackingResultRepositoryImplTest {
    @Test
    void acceptsStatusConfigAndResultsWithoutPersistence() {
        StatusTrackingResultRepositoryImpl repository = new StatusTrackingResultRepositoryImpl();

        assertThatCode(() -> {
            repository.createTable(StatusTrackingConfig.builder()
                    .unitCode("CP1").trackingType(TrackingType.STATUS).build());
            repository.save(Collections.singletonList(StatusResult.builder()
                    .unitCode("CP1").trackingType(TrackingType.STATUS).productNo(1).build()));
        }).doesNotThrowAnyException();
    }
}
