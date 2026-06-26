package com.wisdri.tracking.domain.repository.tracking;

import com.wisdri.tracking.common.exception.TrackingException;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingResult;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingResultRepositoryDispatcherTest {

    @Test
    void createTableThrowsTrackingExceptionWhenTrackingTypeUnsupported() {
        TrackingResultRepositoryDispatcher dispatcher = dispatcherWithUnsupportedRepository();
        ProcessTrackingConfig config = ProcessTrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();

        assertThatThrownBy(() -> dispatcher.createTable(config, PointSnapshot.builder().build()))
                .isInstanceOf(TrackingException.class)
                .hasMessageContaining("不支持的跟踪结果表类型");
    }

    @Test
    void saveThrowsTrackingExceptionWhenTrackingTypeUnsupported() {
        TrackingResultRepositoryDispatcher dispatcher = dispatcherWithUnsupportedRepository();
        List<ProcessResult> results = Collections.singletonList(ProcessResult.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .generatedAt(Instant.now())
                .build());

        assertThatThrownBy(() -> dispatcher.save(results))
                .isInstanceOf(TrackingException.class)
                .hasMessageContaining("不支持的跟踪结果类型");
    }

    private TrackingResultRepositoryDispatcher dispatcherWithUnsupportedRepository() {
        TrackingResultRepositoryDispatcher dispatcher = new TrackingResultRepositoryDispatcher();
        ReflectionTestUtils.setField(dispatcher, "repositories", Collections.singletonList(new UnsupportedRepository()));
        return dispatcher;
    }

    private static class UnsupportedRepository implements TrackingResultRepository<ProcessTrackingConfig, ProcessResult> {
        @Override
        public boolean support(TrackingType trackingType) {
            return false;
        }

        @Override
        public void createTable(ProcessTrackingConfig config, PointSnapshot latestSnapshot) {
        }

        @Override
        public void save(List<ProcessResult> results) {
        }
    }
}
