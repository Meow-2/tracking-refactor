package com.wisdri.tracking.infrastructure.repository.point;

import com.wisdri.tracking.domain.model.point.PointSnapshot;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LastPointSnapshotRepositoryImplTest {
    private final Map<String, String> redis = new ConcurrentHashMap<>();
    private LastPointSnapshotRepositoryImpl repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());
        repository = new LastPointSnapshotRepositoryImpl();
        ReflectionTestUtils.setField(repository, "stringRedisTemplate", redisTemplate);
    }

    @Test
    void isolatesBatchSnapshotsByTemplateCode() {
        repository.save("BAF1", TrackingType.BATCH, "fb1", snapshot("fb1"));
        repository.save("BAF1", TrackingType.BATCH, "fb2", snapshot("fb2"));

        assertThat(redis).containsKeys(
                "tracking:baf1:batch:fb1:lastdata",
                "tracking:baf1:batch:fb2:lastdata"
        );
        assertThat(repository.find("BAF1", TrackingType.BATCH, "fb1")
                .orElseThrow(AssertionError::new).getValues()).containsEntry("source", "fb1");
        assertThat(repository.find("BAF1", TrackingType.BATCH, "fb2")
                .orElseThrow(AssertionError::new).getValues()).containsEntry("source", "fb2");
    }

    @Test
    void keepsOriginalKeyForTrackingWithoutTemplateCode() {
        repository.save("CP1", TrackingType.PROCESS, snapshot("process"));

        assertThat(redis).containsKey("tracking:cp1:process:lastdata");
        assertThat(redis.get("tracking:cp1:process:lastdata"))
                .contains("\"receivedAt\" : \"2026-07-17T16:00:00+08:00\"");
        assertThat(repository.find("CP1", TrackingType.PROCESS))
                .get()
                .extracting(PointSnapshot::getReceivedAt)
                .isEqualTo(Instant.parse("2026-07-17T08:00:00Z"));
    }

    private PointSnapshot snapshot(String source) {
        return PointSnapshot.builder()
                .values(Collections.singletonMap("source", source))
                .receivedAt(Instant.parse("2026-07-17T08:00:00Z"))
                .build();
    }
}
