package com.wisdri.tracking.infrastructure.repository.tracking;

import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.infrastructure.dto.postgres.shear.QmShearLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ShearTrackingResultRepositoryImplTest {
    @Test
    void mapsAllShearBusinessFields() {
        ShearTrackingResultRepositoryImpl repository = new ShearTrackingResultRepositoryImpl();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        ShearResult result = ShearResult.builder()
                .unitCode("LINE-X").trackingType(TrackingType.SHEAR)
                .inMatNo("MAT-1").inMatNoProdNo("3")
                .shearType(711).shearLength(new BigDecimal("2.5")).setNumber(4).shearTime(time)
                .porCoilNo("POR-1").porColorCode("10")
                .trCoilNo("TR-1").trColorCode("20")
                .porRemainLength(new BigDecimal("500")).porMaxLength(new BigDecimal("1000"))
                .trRemainLength(new BigDecimal("200")).trMaxLength(new BigDecimal("800"))
                .cutNo(2).shearNo(1).build();

        QmShearLogEntity entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", result);

        assertThat(entity.getUnitCode()).isEqualTo("LINE-X");
        assertThat(entity.getInMatNo()).isEqualTo("MAT-1");
        assertThat(entity.getInMatNoProdNo()).isEqualTo("3");
        assertThat(entity.getShearType()).isEqualTo(711);
        assertThat(entity.getShearLength()).isEqualByComparingTo("2.5");
        assertThat(entity.getSetNumber()).isEqualTo(4);
        assertThat(entity.getShearTime()).isEqualTo(time);
        assertThat(entity.getPorCoilNo()).isEqualTo("POR-1");
        assertThat(entity.getTrCoilNo()).isEqualTo("TR-1");
        assertThat(entity.getCutNo()).isEqualTo(2);
        assertThat(entity.getShearNo()).isEqualTo(1);
    }

    @Test
    void disabledShearStorageDoesNotOpenTransaction() {
        ShearTrackingResultRepositoryImpl repository = new ShearTrackingResultRepositoryImpl();
        TrackingProperties properties = new TrackingProperties();
        properties.getStorage().getShear().setEnabled(false);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(repository, "trackingProperties", properties);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);

        repository.save(Arrays.asList(ShearResult.builder()
                .trackingType(TrackingType.SHEAR).build()));

        verifyNoInteractions(transactionTemplate);
    }
}
