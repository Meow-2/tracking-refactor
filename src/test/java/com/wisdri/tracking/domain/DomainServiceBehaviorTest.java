package com.wisdri.tracking.domain;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.abnormal.AbnormalType;
import com.wisdri.tracking.domain.model.config.LengthMode;
import com.wisdri.tracking.domain.model.config.RollingConfig;
import com.wisdri.tracking.domain.model.config.SegmentConfig;
import com.wisdri.tracking.domain.model.config.StartCondition;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.config.TrackingPointGroup;
import com.wisdri.tracking.domain.model.config.TrackingSection;
import com.wisdri.tracking.domain.model.event.TrackingEvent;
import com.wisdri.tracking.domain.model.event.TrackingEventType;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.model.tracking.TrackingInput;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.process.ProcessResult;
import com.wisdri.tracking.domain.service.abnormal.impl.AbnormalDataDetectorImpl;
import com.wisdri.tracking.domain.service.event.impl.TrackingEventDetectorImpl;
import com.wisdri.tracking.domain.service.point.impl.PointExtractorImpl;
import com.wisdri.tracking.domain.service.process.impl.ProcessTrackingAlgorithmImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DomainServiceBehaviorTest {

    @Test
    void pointExtractorExtractsConfiguredPointsAndNormalizesValues() {
        PointExtractorImpl extractor = new PointExtractorImpl();
        TrackingConfig config = processConfig(LengthMode.WELDER);
        Map<String, Object> rawValues = new LinkedHashMap<>();
        rawValues.put("/aygg_tracking/cp1/process/tracking/speed", 1.25);
        rawValues.put("/aygg_tracking/cp1/process/tracking/group1_coil_no", "\u00004605000400E ");
        rawValues.put("/aygg_tracking/cp1/process/tracking/group1_len_1", 100);
        rawValues.put("/aygg_tracking/cp1/process/tech/sf/sf_temp", 780);

        PointSnapshot snapshot = extractor.extract(rawValues, config);

        assertThat(snapshot.value("speed").map(PointValue::decimalValue)).contains(new BigDecimal("1.25"));
        assertThat(snapshot.value("group1_coil_no").map(PointValue::stringValue)).contains("4605000400E");
        assertThat(snapshot.value("group1_len_1").map(PointValue::decimalValue)).contains(new BigDecimal("100"));
        assertThat(snapshot.value("sf_temp").map(PointValue::decimalValue)).contains(new BigDecimal("780"));
    }

    @Test
    void eventDetectorDetectsCoilSetChanged() {
        TrackingEventDetectorImpl detector = new TrackingEventDetectorImpl();
        TrackingConfig config = processConfig(LengthMode.WELDER);
        PointSnapshot previous = snapshot(mapOf(
                "group1_coil_no", "A",
                "group2_coil_no", "B"
        ));
        PointSnapshot latest = snapshot(mapOf(
                "group1_coil_no", "A",
                "group2_coil_no", "C"
        ));

        List<TrackingEvent> events = detector.detect(latest, previous, config);

        assertThat(events).extracting(TrackingEvent::getEventType).containsExactly(TrackingEventType.COIL_SET_CHANGED);
    }

    @Test
    void processAlgorithmCalculatesWelderResultsForEachSegment() {
        ProcessTrackingAlgorithmImpl algorithm = new ProcessTrackingAlgorithmImpl();
        TrackingConfig config = processConfig(LengthMode.WELDER);
        PointSnapshot latest = snapshot(mapOf(
                "speed", 1.2,
                "group1_coil_no", "A",
                "group1_len_1", 100,
                "group1_len_2", 300,
                "group2_coil_no", "B",
                "group2_len_1", 20,
                "group2_len_2", 200,
                "sf_temp", 780,
                "rtf_temp", 810
        ));

        List<ProcessResult> results = algorithm.calculate(TrackingInput.builder()
                .config(config)
                .latestSnapshot(latest)
                .build());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getSegmentName()).isEqualTo("SF段");
        assertThat(results.get(0).getCoilNo()).isEqualTo("A");
        assertThat(results.get(0).getHeadLength()).isEqualByComparingTo(new BigDecimal("50"));
        assertThat(results.get(0).getParameters()).containsEntry("sf_temp", 780);
        assertThat(results.get(1).getSegmentName()).isEqualTo("RTF段");
        assertThat(results.get(1).getCoilNo()).isEqualTo("B");
        assertThat(results.get(1).getHeadLength()).isEqualByComparingTo(new BigDecimal("90"));
    }

    @Test
    void processAlgorithmStopsWhenStartConditionNotReached() {
        ProcessTrackingAlgorithmImpl algorithm = new ProcessTrackingAlgorithmImpl();
        TrackingConfig config = processConfig(LengthMode.WELDER);
        PointSnapshot latest = snapshot(mapOf("speed", 0.1));

        List<ProcessResult> results = algorithm.calculate(TrackingInput.builder()
                .config(config)
                .latestSnapshot(latest)
                .build());

        assertThat(results).isEmpty();
    }

    @Test
    void abnormalDetectorReportsNullValues() {
        AbnormalDataDetectorImpl detector = new AbnormalDataDetectorImpl();
        TrackingConfig config = processConfig(LengthMode.WELDER);
        PointSnapshot latest = snapshot(mapOf("speed", null));

        List<AbnormalData> abnormalData = detector.detect(latest, null, config);

        assertThat(abnormalData).hasSize(1);
        assertThat(abnormalData.get(0).getPointCode()).isEqualTo("speed");
        assertThat(abnormalData.get(0).getAbnormalType()).isEqualTo(AbnormalType.NULL_DATA);
    }

    private static TrackingConfig processConfig(LengthMode lengthMode) {
        return TrackingConfig.builder()
                .unitCode("CP1")
                .trackingType(TrackingType.PROCESS)
                .enable(Boolean.TRUE)
                .mqttTopic("cp1_process_tracking")
                .tracking(TrackingSection.builder()
                        .pointPrefix("/aygg_tracking/cp1/process/tracking/")
                        .speedPoint("speed")
                        .startCondition(StartCondition.builder()
                                .point("speed")
                                .threshold(new BigDecimal("0.7"))
                                .build())
                        .lengthMode(lengthMode)
                        .rolling(RollingConfig.builder()
                                .directPoint("direct")
                                .passNoPoint("pass_no")
                                .directReverse(Boolean.FALSE)
                                .build())
                        .points(Arrays.asList(
                                TrackingPointGroup.builder()
                                        .lengthPoints(Arrays.asList("group1_len_1", "group1_len_2"))
                                        .coilNoPoint("group1_coil_no")
                                        .rollingCoiler(Boolean.TRUE)
                                        .build(),
                                TrackingPointGroup.builder()
                                        .lengthPoints(Arrays.asList("group2_len_1", "group2_len_2"))
                                        .coilNoPoint("group2_coil_no")
                                        .rollingCoiler(Boolean.FALSE)
                                        .build()
                        ))
                        .build())
                .segments(Arrays.asList(
                        SegmentConfig.builder()
                                .name("SF段")
                                .pointPrefix("/aygg_tracking/cp1/process/tech/sf/")
                                .lengthCorrect(new BigDecimal("-50"))
                                .lengthArrayIndex(0)
                                .points(Collections.singletonList("sf_temp"))
                                .build(),
                        SegmentConfig.builder()
                                .name("RTF段")
                                .pointPrefix("/aygg_tracking/cp1/process/tech/rtf/")
                                .lengthCorrect(new BigDecimal("-110"))
                                .lengthArrayIndex(1)
                                .points(Collections.singletonList("rtf_temp"))
                                .build()
                ))
                .build();
    }

    private static PointSnapshot snapshot(Map<String, Object> rawValues) {
        Map<String, PointValue> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            values.put(entry.getKey(), PointValue.of(entry.getValue()));
        }
        return PointSnapshot.builder().values(values).build();
    }

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }
}
