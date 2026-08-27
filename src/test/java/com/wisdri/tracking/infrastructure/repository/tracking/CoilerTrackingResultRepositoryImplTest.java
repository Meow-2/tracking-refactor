package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.infrastructure.dto.postgres.coiler.QmCoilerLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CoilerTrackingResultRepositoryImplTest {
    @Test
    void mapsAllCoilerBusinessFields() {
        CoilerTrackingResultRepositoryImpl repository = new CoilerTrackingResultRepositoryImpl();
        Instant time = Instant.parse("2026-08-27T01:00:00Z");
        CoilerResult result = CoilerResult.builder()
                .unitCode("CP1").trackingType(TrackingType.COILER).receivedAt(time)
                .inMatNo("COIL-1").inMatNoProdNo(3)
                .coilerMethod("99").coilerMethodName("下卷取")
                .deviceCode("tr1").deviceName("1#卷取机")
                .maxLength(new BigDecimal("800.500")).build();

        QmCoilerLogEntity entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", result);

        assertThat(entity.getUnitCode()).isEqualTo("CP1");
        assertThat(entity.getInMatNo()).isEqualTo("COIL-1");
        assertThat(entity.getInMatNoProdNo()).isEqualTo("3");
        assertThat(entity.getCoilerMethod()).isEqualTo("99");
        assertThat(entity.getCoilerMethodName()).isEqualTo("下卷取");
        assertThat(entity.getDeviceCode()).isEqualTo("tr1");
        assertThat(entity.getDeviceName()).isEqualTo("1#卷取机");
        assertThat(entity.getMaxLength()).isEqualTo("800.500");
        assertThat(entity.getCreateTime()).isEqualTo(time);
    }

    @Test
    void disabledCoilerStorageDoesNotOpenTransaction() {
        CoilerTrackingResultRepositoryImpl repository = new CoilerTrackingResultRepositoryImpl();
        TrackingProperties properties = new TrackingProperties();
        properties.getStorage().getCoiler().setEnabled(false);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(repository, "trackingProperties", properties);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);

        repository.save(Collections.singletonList(CoilerResult.builder()
                .trackingType(TrackingType.COILER).build()));

        verifyNoInteractions(transactionTemplate);
    }
}
