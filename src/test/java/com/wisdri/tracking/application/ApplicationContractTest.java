package com.wisdri.tracking.application;

import com.wisdri.tracking.application.config.TrackingConfigRefreshService;
import com.wisdri.tracking.application.retracking.ReTrackingApplicationService;
import com.wisdri.tracking.application.retracking.command.ReTrackingCommand;
import com.wisdri.tracking.application.tracking.MqttPointMessageApplicationService;
import com.wisdri.tracking.application.tracking.TrackingTaskApplicationService;
import com.wisdri.tracking.application.tracking.TrackingWorkerManager;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.tracking.TrackingTask;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContractTest {

    @Test
    void applicationServicesExposeWorkflowBoundaries() {
        RecordingMqttPointMessageApplicationService mqttService = new RecordingMqttPointMessageApplicationService();
        RecordingTrackingTaskApplicationService taskService = new RecordingTrackingTaskApplicationService();
        RecordingTrackingWorkerManager workerManager = new RecordingTrackingWorkerManager();

        Map<String, Object> rawValues = Collections.singletonMap("speed", 1.2);
        TrackingTask task = TrackingTask.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();

        mqttService.handle("CP1", TrackingType.PROCESS, rawValues);
        taskService.handle(task);
        workerManager.start("CP1", TrackingType.PROCESS, "cp1_process_tracking");
        workerManager.stop("CP1", TrackingType.PROCESS);

        assertThat(mqttService.rawValues).isSameAs(rawValues);
        assertThat(taskService.task).isSameAs(task);
        assertThat(workerManager.startedTopic).isEqualTo("cp1_process_tracking");
        assertThat(workerManager.stopped).isTrue();
    }

    @Test
    void configRefreshAndRetrackingContractsUseDomainTypes() {
        RecordingConfigRefreshService configRefreshService = new RecordingConfigRefreshService();
        RecordingReTrackingApplicationService reTrackingService = new RecordingReTrackingApplicationService();
        ReTrackingCommand command = ReTrackingCommand.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .startTime(Instant.parse("2026-06-17T08:00:00Z"))
                .endTime(Instant.parse("2026-06-17T09:00:00Z"))
                .build();

        Optional<TrackingConfig> config = configRefreshService.refresh("CP1", TrackingType.PROCESS);
        reTrackingService.retrack(command);

        assertThat(config).containsSame(configRefreshService.config);
        assertThat(reTrackingService.command).isSameAs(command);
    }

    private static class RecordingMqttPointMessageApplicationService implements MqttPointMessageApplicationService {
        private Map<String, Object> rawValues;

        @Override
        public void handle(String unitCode, TrackingType trackingType, Map<String, Object> rawValues) {
            this.rawValues = rawValues;
        }
    }

    private static class RecordingTrackingTaskApplicationService implements TrackingTaskApplicationService {
        private TrackingTask task;

        @Override
        public void handle(TrackingTask task) {
            this.task = task;
        }
    }

    private static class RecordingTrackingWorkerManager implements TrackingWorkerManager {
        private String startedTopic;
        private boolean stopped;

        @Override
        public void start(String unitCode, TrackingType trackingType, String mqttTopic) {
            this.startedTopic = mqttTopic;
        }

        @Override
        public void stop(String unitCode, TrackingType trackingType) {
            this.stopped = true;
        }
    }

    private static class RecordingConfigRefreshService implements TrackingConfigRefreshService {
        private final TrackingConfig config = TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .build();

        @Override
        public Optional<TrackingConfig> refresh(String unitCode, TrackingType trackingType) {
            return Optional.of(config);
        }
    }

    private static class RecordingReTrackingApplicationService implements ReTrackingApplicationService {
        private ReTrackingCommand command;

        @Override
        public void retrack(ReTrackingCommand command) {
            this.command = command;
        }
    }
}
