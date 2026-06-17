package com.wisdri.tracking.integration.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.LengthMode;
import com.wisdri.tracking.domain.model.config.TrackingConfig;
import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.point.PointValue;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAdapterTest {

    @Test
    void redisKeyBuilderBuildsTrackingKeys() {
        RedisKeyBuilder keyBuilder = new RedisKeyBuilder();

        assertThat(keyBuilder.configKey("CP1", TrackingType.PROCESS))
                .isEqualTo("tracking:cp1:process:config");
        assertThat(keyBuilder.lastDataKey("CP1", TrackingType.PROCESS))
                .isEqualTo("tracking:cp1:process:lastdata");
    }

    @Test
    void trackingConfigMapperConvertsRedisJsonToDomainConfig() {
        RedisTrackingConfigMapper mapper = trackingConfigMapper();
        String json = "{"
                + "\"enable\":true,"
                + "\"mqtt_topic\":\"cp1_process_tracking\","
                + "\"tracking\":{"
                + "\"point_prefix\":\"/aygg_tracking/cp1/process/tracking/\","
                + "\"speed_point\":\"center_speed_pv\","
                + "\"start_condition\":{\"point\":\"center_speed_pv\",\"threshold\":0.7},"
                + "\"length_mode\":\"welder\","
                + "\"points\":[{\"length\":[\"group1_len_1\",\"group1_len_2\"],\"coil_no\":\"group1_coil_no\"}]"
                + "},"
                + "\"segments\":[{"
                + "\"name\":\"SF段\","
                + "\"point_prefix\":\"/aygg_tracking/cp1/process/tech/sf/\","
                + "\"length_correct\":-50,"
                + "\"length_array_index\":0,"
                + "\"points\":[\"sf_plate_temp\"]"
                + "}]"
                + "}";

        TrackingConfig config = mapper.fromJson("CP1", TrackingType.PROCESS, json);

        assertThat(config.getUnitCode()).isEqualTo("CP1");
        assertThat(config.getTrackingType()).isEqualTo(TrackingType.PROCESS);
        assertThat(config.getMqttTopic()).isEqualTo("cp1_process_tracking");
        assertThat(config.getTracking().getLengthMode()).isEqualTo(LengthMode.WELDER);
        assertThat(config.getTracking().getPoints().get(0).getLengthPoints())
                .containsExactly("group1_len_1", "group1_len_2");
        assertThat(config.getSegments().get(0).getLengthCorrect()).isEqualByComparingTo(new BigDecimal("-50"));
    }

    @Test
    void pointSnapshotMapperConvertsRedisLastDataJsonToDomainSnapshot() {
        RedisPointSnapshotMapper mapper = pointSnapshotMapper();

        PointSnapshot snapshot = mapper.fromJson("{\"coil_no\":\"4605000400E\",\"speed\":1.25,\"direct\":1}");

        assertThat(snapshot.value("coil_no").map(PointValue::stringValue)).contains("4605000400E");
        assertThat(snapshot.value("speed").map(PointValue::decimalValue)).contains(new BigDecimal("1.25"));
        assertThat(snapshot.value("direct").map(PointValue::booleanValue)).contains(Boolean.TRUE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisRepositoriesReadAndWriteJsonThroughStringRedisTemplate() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisKeyBuilder keyBuilder = new RedisKeyBuilder();
        RedisTrackingConfigMapper configMapper = trackingConfigMapper();
        RedisPointSnapshotMapper snapshotMapper = pointSnapshotMapper();
        RedisTrackingConfigRepository configRepository = new RedisTrackingConfigRepository();
        ReflectionTestUtils.setField(configRepository, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(configRepository, "keyBuilder", keyBuilder);
        ReflectionTestUtils.setField(configRepository, "mapper", configMapper);
        RedisLastPointSnapshotRepository snapshotRepository = new RedisLastPointSnapshotRepository();
        ReflectionTestUtils.setField(snapshotRepository, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(snapshotRepository, "keyBuilder", keyBuilder);
        ReflectionTestUtils.setField(snapshotRepository, "mapper", snapshotMapper);
        when(valueOperations.get("tracking:cp1:process:config")).thenReturn("{\"enable\":true,\"mqtt_topic\":\"cp1_process_tracking\"}");
        when(valueOperations.get("tracking:cp1:process:lastdata")).thenReturn("{\"speed\":1.25}");

        Optional<TrackingConfig> config = configRepository.find("CP1", TrackingType.PROCESS);
        Optional<PointSnapshot> snapshot = snapshotRepository.find("CP1", TrackingType.PROCESS);
        snapshotRepository.save("CP1", TrackingType.PROCESS, snapshot.get());

        assertThat(config).isPresent();
        assertThat(snapshot).isPresent();
        verify(valueOperations).set("tracking:cp1:process:lastdata", "{\"speed\":1.25}");
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static RedisTrackingConfigMapper trackingConfigMapper() {
        RedisTrackingConfigMapper mapper = new RedisTrackingConfigMapper();
        ReflectionTestUtils.setField(mapper, "objectMapper", objectMapper());
        return mapper;
    }

    private static RedisPointSnapshotMapper pointSnapshotMapper() {
        RedisPointSnapshotMapper mapper = new RedisPointSnapshotMapper();
        ReflectionTestUtils.setField(mapper, "objectMapper", objectMapper());
        return mapper;
    }
}
